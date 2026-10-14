const mongoose = require('mongoose');

const reportSchema = new mongoose.Schema({
  reportId: {
    type: String,
    required: true,
    unique: true,
    default: () => `#CDO-2026-${Math.floor(1000 + Math.random() * 9000)}`
  },
  title: {
    type: String,
    required: [true, 'Report title / location description is required']
  },
  category: {
    type: String,
    enum: ['Illegal Dump Site', 'Overflowing Bins', 'Clogged Drainage Waste', 'Uncollected Household Trash', 'Others'],
    default: 'Illegal Dump Site'
  },
  barangay: {
    type: String,
    required: true,
    default: 'Carmen'
  },
  purokZone: {
    type: String,
    default: ''
  },
  landmark: {
    type: String,
    default: ''
  },
  status: {
    type: String,
    enum: ['PENDING', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'],
    default: 'PENDING'
  },
  description: {
    type: String,
    default: ''
  },
  gpsCoordinates: {
    type: String,
    default: '8.4822° N, 124.6175° E'
  },
  unitAssigned: {
    type: String,
    default: 'Unassigned'
  },
  notes: {
    type: String,
    default: ''
  },
  photoUrl: {
    type: String,
    default: ''
  },
  userEmail: {
    type: String,
    lowercase: true,
    trim: true,
    default: ''
  },
  user: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User'
  }
}, {
  timestamps: true
});

module.exports = mongoose.model('Report', reportSchema);
