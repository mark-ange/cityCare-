const express = require('express');
const mongoose = require('mongoose');
const router = express.Router();
const Report = require('../models/Report');
const User = require('../models/User');
const Notification = require('../models/Notification');
const { connection: reportConnection } = require('../config/reportDb');
const ReportingUser = reportConnection.model('User', User.schema);

const MAX_REPORT_PHOTO_BASE64_LENGTH = 5600000;

const toReportPhotoDataUrl = (photoBase64) => {
  if (!photoBase64) return '';
  if (photoBase64.length > MAX_REPORT_PHOTO_BASE64_LENGTH ||
    !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(photoBase64)) {
    return null;
  }

  const buffer = Buffer.from(photoBase64, 'base64');
  if (!buffer.length || buffer.length > 4 * 1024 * 1024 || buffer.toString('base64') !== photoBase64) {
    return null;
  }

  let contentType;
  if (buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) {
    contentType = 'image/jpeg';
  } else if (buffer.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))) {
    contentType = 'image/png';
  } else if (buffer.toString('ascii', 0, 4) === 'RIFF' && buffer.toString('ascii', 8, 12) === 'WEBP') {
    contentType = 'image/webp';
  } else {
    return null;
  }

  return `data:${contentType};base64,${photoBase64}`;
};

function requireDatabase(res) {
  if (Report.db.readyState === 1) return true;

  res.status(503).json({
    success: false,
    persisted: false,
    message: 'Report storage is unavailable. The report was not saved; retry when the database is online.'
  });
  return false;
}

async function notifyResidentReportReceived(report, email) {
  if (!email) return;
  try {
    const user = await User.findOne({ email: email.toLowerCase().trim() }).select('_id');
    const reportingUser = await ReportingUser.findOne({ email: email.toLowerCase().trim() }).select('_id');
    if (reportingUser && String(report.user || '') !== String(reportingUser._id)) {
      report.user = reportingUser._id;
      await report.save();
    }
    if (!user) return;
    const title = 'Report received by CLENRO';
    const body = `Your report ${report.reportId} was received and is pending review.`;
    if (await Notification.exists({ user: user._id, title, body, type: 'REPORT_UPDATE' })) {
      return;
    }
    await Notification.create({
      user: user._id,
      title,
      body,
      type: 'REPORT_UPDATE'
    });
  } catch (error) {
    console.error('Report was saved, but its resident notification could not be created:', error.message);
  }
}

// @route   GET /api/reports
// @desc    Get all incident waste reports
// @access  Public or Protected
router.get('/', async (req, res) => {
  if (!requireDatabase(res)) return;

  try {
    const { barangay, status, userEmail } = req.query;
    const filter = {};
    if (barangay) filter.barangay = barangay;
    if (status) filter.status = status;
    if (userEmail) filter.userEmail = userEmail.toLowerCase().trim();

    const reports = await Report.find(filter).sort({ createdAt: -1 });
    res.json({
      success: true,
      count: reports.length,
      data: reports
    });
  } catch (error) {
    console.error('Failed to load reports from MongoDB:', error.message);
    res.status(500).json({ success: false, message: 'Failed to load reports from the database.' });
  }
});

// @route   POST /api/reports
// @desc    Create a new incident report
// @access  Public or Protected
router.post('/', async (req, res) => {
  if (!requireDatabase(res)) return;

  try {
    const {
      title,
      category,
      barangay,
      purokZone,
      landmark,
      description,
      gpsCoordinates,
      photoUrl,
      photoBase64,
      userEmail,
      reportId
    } = req.body;
    const generatedId = reportId || `#CDO-2026-${Math.floor(1000 + Math.random() * 9000)}`;
    const uploadedPhoto = toReportPhotoDataUrl(photoBase64);
    if (uploadedPhoto === null) {
      return res.status(400).json({
        success: false,
        persisted: false,
        message: 'Photo must be a valid JPEG, PNG, or WebP image no larger than 4 MB.'
      });
    }

    const existingReport = await Report.findOne({ reportId: generatedId });
    if (existingReport) {
      if (uploadedPhoto && !existingReport.photoUrl) {
        existingReport.photoUrl = uploadedPhoto;
        await existingReport.save();
      }
      await notifyResidentReportReceived(existingReport, existingReport.userEmail);
      return res.status(200).json({
        success: true,
        persisted: true,
        message: 'Report has already been saved',
        data: {
          ...existingReport.toObject(),
          hasPhoto: Boolean(existingReport.photoUrl)
        }
      });
    }

    const report = await Report.create({
      reportId: generatedId,
      title: title || `Brgy. ${barangay || 'Carmen'}, ${purokZone || 'Zone 1'}`,
      category: category || 'Illegal Dump Site',
      barangay: barangay || 'Carmen',
      purokZone: purokZone || '',
      landmark: landmark || '',
      status: 'PENDING',
      description: description || 'Uncollected waste report from resident.',
      gpsCoordinates: gpsCoordinates || '8.4822° N, 124.6175° E',
      unitAssigned: 'Unassigned',
      photoUrl: uploadedPhoto || photoUrl || '',
      userEmail: (userEmail || req.user?.email || '').toLowerCase().trim(),
      user: null
    });

    await notifyResidentReportReceived(report, report.userEmail);

    return res.status(201).json({
      success: true,
      persisted: true,
      message: 'Report submitted successfully to CLENRO',
      data: {
        ...report.toObject(),
        hasPhoto: Boolean(report.photoUrl)
      }
    });
  } catch (error) {
    if (error.code === 11000 && req.body.reportId) {
      try {
        const existingReport = await Report.findOne({ reportId: req.body.reportId });
        if (existingReport) {
          return res.status(200).json({
            success: true,
            persisted: true,
            message: 'Report has already been saved',
            data: existingReport
          });
        }
      } catch (lookupError) {
        console.error('Failed to verify an existing report in MongoDB:', lookupError.message);
      }
    }

    console.error('Failed to save report to MongoDB:', error.message);
    const statusCode = error.name === 'ValidationError' ? 400 : 500;
    return res.status(statusCode).json({
      success: false,
      persisted: false,
      message: statusCode === 400 ? error.message : 'Failed to save report to the database.'
    });
  }
});

// @route   PUT /api/reports/:id/status
// @desc    Update report status and assigned unit
// @access  Public / Admin
router.put('/:id/status', async (req, res) => {
  if (!requireDatabase(res)) return;

  try {
    const { status, unitAssigned, notes } = req.body;
    const reportId = req.params.id;
    const filter = mongoose.isValidObjectId(reportId)
      ? { $or: [{ _id: reportId }, { reportId }] }
      : { reportId };
    const report = await Report.findOne(filter);

    if (!report) {
      return res.status(404).json({ success: false, message: 'Report not found' });
    }

    const previousStatus = report.status;
    const previousUnitAssigned = report.unitAssigned;
    if (status) report.status = status;
    if (unitAssigned) report.unitAssigned = unitAssigned;
    if (typeof notes === 'string') report.notes = notes;
    await report.save();

    const statusChanged = report.status !== previousStatus;
    const assignmentChanged = report.unitAssigned !== previousUnitAssigned;
    if (statusChanged || assignmentChanged) {
      const recipient = report.user
        ? await User.findById(report.user).select('_id')
        : report.userEmail
          ? await User.findOne({ email: report.userEmail.trim().toLowerCase() }).select('_id')
          : null;

      if (recipient) {
        const statusLabels = {
          REJECTED: 'rejected',
          ASSIGNED: 'assigned',
          IN_PROGRESS: 'in progress',
          RESOLVED: 'resolved',
          PENDING: 'updated'
        };
        const statusLabel = statusLabels[report.status] || report.status.toLowerCase().replace(/_/g, ' ');
        const title = statusChanged
          ? `Your report was ${statusLabel}`
          : 'Your report was assigned';
        const detail = report.notes?.trim() ||
          (report.unitAssigned && report.unitAssigned !== 'Unassigned'
            ? `Assigned to ${report.unitAssigned}.`
            : `The status of "${report.title}" is now ${statusLabel}.`);

        await Notification.create({
          user: recipient._id,
          title,
          body: detail,
          type: 'REPORT_UPDATE'
        });
      }
    }

    return res.json({ success: true, persisted: true, message: 'Report status updated', data: report });
  } catch (error) {
    console.error('Failed to update report in MongoDB:', error.message);
    const statusCode = error.name === 'ValidationError' ? 400 : 500;
    return res.status(statusCode).json({
      success: false,
      message: statusCode === 400 ? error.message : 'Failed to update report in the database.'
    });
  }
});

module.exports = router;
