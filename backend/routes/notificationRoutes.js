const express = require('express');
const mongoose = require('mongoose');
const router = express.Router();
const Notification = require('../models/Notification');
const { protect } = require('../middleware/authMiddleware');

router.get('/', protect, async (req, res) => {
  try {
    if (!req.user) {
      return res.status(401).json({ success: false, message: 'Not authorized.' });
    }
    const notifications = await Notification.find({ user: req.user._id })
      .sort({ createdAt: -1 })
      .limit(100)
      .select('title body type isRead createdAt');

    return res.json({
      success: true,
      data: notifications.map(notification => ({
        id: notification._id.toString(),
        title: notification.title,
        body: notification.body,
        type: notification.type,
        isRead: notification.isRead,
        time: notification.createdAt.toISOString()
      }))
    });
  } catch (error) {
    console.error('Failed to load notifications:', error.message);
    return res.status(500).json({
      success: false,
      message: 'Failed to load your notifications.'
    });
  }
});

router.patch('/read-all', protect, async (req, res) => {
  try {
    if (!req.user) {
      return res.status(401).json({ success: false, message: 'Not authorized.' });
    }
    const result = await Notification.updateMany(
      { user: req.user._id, isRead: false },
      { $set: { isRead: true } }
    );
    return res.json({ success: true, modifiedCount: result.modifiedCount });
  } catch (error) {
    console.error('Failed to mark notifications as read:', error.message);
    return res.status(500).json({
      success: false,
      message: 'Could not mark notifications as read.'
    });
  }
});

router.patch('/:id/read', protect, async (req, res) => {
  try {
    if (!req.user) {
      return res.status(401).json({ success: false, message: 'Not authorized.' });
    }
    if (!mongoose.isValidObjectId(req.params.id)) {
      return res.status(400).json({ success: false, message: 'Invalid notification ID.' });
    }
    const notification = await Notification.findOneAndUpdate(
      { _id: req.params.id, user: req.user._id },
      { $set: { isRead: true } },
      { new: true }
    );
    if (!notification) {
      return res.status(404).json({ success: false, message: 'Notification not found.' });
    }
    return res.json({ success: true, id: notification._id.toString(), isRead: notification.isRead });
  } catch (error) {
    console.error('Failed to mark notification as read:', error.message);
    return res.status(500).json({
      success: false,
      message: 'Could not mark this notification as read.'
    });
  }
});

module.exports = router;
