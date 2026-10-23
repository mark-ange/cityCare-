const express = require('express');
const mongoose = require('mongoose');
const router = express.Router();
const CommunityContent = require('../models/CommunityContent');
const Notification = require('../models/Notification');
const User = require('../models/User');
const { protect, adminOnly } = require('../middleware/authMiddleware');

const contentTypes = new Set(['recycle', 'event', 'tip', 'blog']);
const contentLabels = {
  recycle: 'Recycling update',
  event: 'New community event',
  tip: 'New waste-reduction tip',
  blog: 'New CityCare blog post'
};

const serializeContent = (record) => {
  const content = record.toObject();
  content.images = (record.images || []).map((image) => ({
    id: String(image._id),
    name: image.name,
    mimeType: image.mimeType,
    size: image.size,
    url: `/api/content/${record._id}/images/${image._id}`
  }));
  return content;
};

router.get('/:id/images/:imageId', async (req, res) => {
  if (!mongoose.isValidObjectId(req.params.id) || !mongoose.isValidObjectId(req.params.imageId)) {
    return res.status(400).json({ success: false, message: 'Content or image ID is invalid.' });
  }

  try {
    const content = await CommunityContent.findOne({
      _id: req.params.id,
      status: 'published'
    }).select('+images.data');
    const image = content?.images.id(req.params.imageId);
    if (!image) return res.status(404).json({ success: false, message: 'Image not found.' });

    res.set('Content-Type', image.mimeType);
    res.set('Content-Length', String(image.data.length));
    res.set('X-Content-Type-Options', 'nosniff');
    res.set('Content-Disposition', 'inline');
    res.set('Cache-Control', 'public, max-age=300');
    return res.send(image.data);
  } catch (error) {
    console.error('Failed to load community content image:', error.message);
    return res.status(500).json({ success: false, message: 'Unable to load community content image.' });
  }
});

router.get('/', async (req, res) => {
  try {
    const { type } = req.query;
    if (type !== undefined && (typeof type !== 'string' || !contentTypes.has(type))) {
      return res.status(400).json({ success: false, message: 'Unsupported content type.' });
    }

    const filter = { status: 'published' };
    if (type) filter.type = type;
    const content = await CommunityContent.find(filter)
      .sort({ updatedAt: -1 })
      .limit(100);
    return res.json({ success: true, count: content.length, data: content.map(serializeContent) });
  } catch (error) {
    console.error('Failed to load community content:', error.message);
    return res.status(500).json({ success: false, message: 'Failed to load community content.' });
  }
});

router.post('/', protect, adminOnly, async (req, res) => {
  try {
    const { type, title, summary, body, imageUrl, sourceUrl, images, eventStartAt, eventEndAt, location } = req.body;
    if (!contentTypes.has(type) || typeof title !== 'string' || !title.trim()) {
      return res.status(400).json({
        success: false,
        message: 'Choose a supported content type and provide a title.'
      });
    }
    if (type === 'event' && (
      typeof eventStartAt !== 'string' ||
      !eventStartAt.trim() ||
      typeof location !== 'string' ||
      !location.trim() ||
      (eventEndAt && new Date(eventEndAt) < new Date(eventStartAt))
    )) {
      return res.status(400).json({
        success: false,
        message: 'Events require a start date and location, and the end date cannot precede the start date.'
      });
    }

    const content = await CommunityContent.create({
      type,
      title: title.trim(),
      summary: typeof summary === 'string' ? summary.trim() : '',
      body: typeof body === 'string' ? body.trim() : '',
      imageUrl: typeof imageUrl === 'string' ? imageUrl.trim() : '',
      sourceUrl: typeof sourceUrl === 'string' ? sourceUrl.trim() : '',
      images: Array.isArray(images) ? images : [],
      status: 'published',
      eventStartAt: typeof eventStartAt === 'string' ? eventStartAt : null,
      eventEndAt: typeof eventEndAt === 'string' ? eventEndAt : null,
      location: typeof location === 'string' ? location.trim() : '',
      createdBy: req.user._id
    });

    const residents = await User.find({ role: 'resident' }).select('_id').lean();
    if (residents.length) {
      await Notification.insertMany(residents.map(resident => ({
        user: resident._id,
        title: contentLabels[type],
        body: content.title,
        type: type.toUpperCase()
      })));
    }

    return res.status(201).json({ success: true, data: serializeContent(content) });
  } catch (error) {
    console.error('Failed to publish community content:', error.message);
    return res.status(500).json({ success: false, message: 'Failed to publish community content.' });
  }
});

module.exports = router;
