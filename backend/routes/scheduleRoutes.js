const express = require('express');
const router = express.Router();
const Schedule = require('../models/Schedule');
const User = require('../models/User');
const Notification = require('../models/Notification');
const { protect, adminOnly } = require('../middleware/authMiddleware');

// @route   GET /api/schedules
// @desc    Get waste collection schedule by barangay
// @access  Public
router.get('/', async (req, res) => {
  try {
    const { barangay } = req.query;
    const escapedBarangay = typeof barangay === 'string'
      ? barangay.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      : '';
    const filter = escapedBarangay
      ? { barangay: { $regex: `^${escapedBarangay}$`, $options: 'i' } }
      : {};
    const schedules = await Schedule.find(filter).sort({ barangay: 1 });

    res.json({
      success: true,
      data: schedules
    });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

router.put('/:barangay', protect, adminOnly, async (req, res) => {
  try {
    const barangay = req.params.barangay.trim();
    if (!barangay) {
      return res.status(400).json({ success: false, message: 'Barangay is required.' });
    }

    const allowedFields = ['biodegradableDays', 'nonBiodegradableDays', 'timeSlot', 'assignedTruck'];
    const updates = {};
    for (const field of allowedFields) {
      if (req.body[field] !== undefined) {
        if (typeof req.body[field] !== 'string' || !req.body[field].trim()) {
          return res.status(400).json({ success: false, message: `${field} must be a non-empty string.` });
        }
        updates[field] = req.body[field].trim();
      }
    }
    if (!Object.keys(updates).length) {
      return res.status(400).json({ success: false, message: 'Provide at least one schedule field to update.' });
    }

    const escapedBarangay = barangay.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    let schedule = await Schedule.findOne({
      barangay: { $regex: `^${escapedBarangay}$`, $options: 'i' }
    });
    const changed = !schedule || Object.entries(updates).some(([field, value]) => schedule[field] !== value);

    if (schedule) {
      Object.assign(schedule, updates);
    } else {
      schedule = new Schedule({ barangay, ...updates });
    }
    await schedule.save();

    if (changed) {
      const residents = await User.find({
        role: 'resident',
        barangay: { $regex: `^${escapedBarangay}$`, $options: 'i' }
      }).select('_id').lean();
      if (residents.length) {
        const changedFields = Object.keys(updates).join(', ');
        await Notification.insertMany(residents.map(resident => ({
          user: resident._id,
          title: 'Collection schedule updated',
          body: `The waste collection schedule for ${schedule.barangay} changed (${changedFields}). Check the Schedule page for details.`,
          type: 'COLLECTION_SCHEDULE'
        })));
      }
    }

    return res.json({ success: true, data: schedule });
  } catch (error) {
    console.error('Failed to update collection schedule:', error.message);
    return res.status(500).json({ success: false, message: 'Failed to update collection schedule.' });
  }
});

module.exports = router;
