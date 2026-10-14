const mongoose = require('mongoose');

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
  }
}, {
  timestamps: true
});

module.exports = mongoose.model('Schedule', scheduleSchema);
