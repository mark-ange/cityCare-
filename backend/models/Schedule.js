const mongoose = require('mongoose');
const { connection } = require('../config/reportDb');

const areaScheduleSchema = new mongoose.Schema({
  areaName: {
    type: String,
    required: true,
    trim: true
  },
  appliesToAllAreas: {
    type: Boolean,
    default: false
  },
  isSample: {
    type: Boolean,
    default: true
  },
  biodegradableDays: {
    type: String,
    required: true,
    trim: true
  },
  nonBiodegradableDays: {
    type: String,
    required: true,
    trim: true
  },
  timeSlot: {
    type: String,
    required: true,
    trim: true
  },
  assignedTruck: {
    type: String,
    required: true,
    trim: true
  }
});

const scheduleSchema = new mongoose.Schema({
  barangay: {
    type: String,
    required: true,
    unique: true
  },
  biodegradableDays: {
    type: String,
    default: 'Monday, Wednesday, Friday'
  },
  nonBiodegradableDays: {
    type: String,
    default: 'Tuesday, Thursday, Saturday'
  },
  timeSlot: {
    type: String,
    default: '06:00 AM - 10:00 AM'
  },
  assignedTruck: {
    type: String,
    default: 'CLENRO Truck #4'
  },
  areaSchedules: {
    type: [areaScheduleSchema],
    default: []
  }
}, {
  timestamps: true
});

module.exports = connection.model('Schedule', scheduleSchema);
