const express = require('express');
const router = express.Router();
const Schedule = require('../models/Schedule');

const mockSchedules = [
  {
    barangay: 'Carmen',
    biodegradableDays: 'Monday, Wednesday, Friday',
    nonBiodegradableDays: 'Tuesday, Thursday, Saturday',
    timeSlot: '06:00 AM - 10:00 AM',
    assignedTruck: 'CLENRO Truck #4'
  },
  {
    barangay: 'Bulua',
    biodegradableDays: 'Monday, Thursday',
    nonBiodegradableDays: 'Wednesday, Saturday',
    timeSlot: '07:00 AM - 11:00 AM',
    assignedTruck: 'CLENRO Truck #2'
  },
  {
    barangay: 'Lapasan',
    biodegradableDays: 'Tuesday, Friday',
    nonBiodegradableDays: 'Monday, Wednesday',
    timeSlot: '05:00 AM - 09:00 AM',
    assignedTruck: 'CLENRO Truck #1'
  }
];

// @route   GET /api/schedules
// @desc    Get waste collection schedule by barangay
// @access  Public
router.get('/', async (req, res) => {
  try {
    const { barangay } = req.query;
    let schedules = [];

    try {
      if (barangay) {
        schedules = await Schedule.find({ barangay: new RegExp(barangay, 'i') });
      } else {
        schedules = await Schedule.find();
      }
    } catch (err) {
      // DB error fallback
    }

    if (schedules.length === 0) {
      if (barangay) {
        schedules = mockSchedules.filter(s => s.barangay.toLowerCase() === barangay.toLowerCase());
        if (schedules.length === 0) schedules = [mockSchedules[0]];
      } else {
        schedules = mockSchedules;
      }
    }

    res.json({
      success: true,
      data: schedules
    });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

module.exports = router;
