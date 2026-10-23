const mongoose = require('mongoose');
const { connection } = require('../config/reportDb');

const galleryImageSchema = new mongoose.Schema({
  name: {
    type: String,
    required: true,
    maxlength: 110
  },
  mimeType: {
    type: String,
    required: true,
    enum: ['image/jpeg', 'image/png', 'image/webp']
  },
  size: {
    type: Number,
    required: true,
    min: 1,
    max: 2 * 1024 * 1024
  },
  data: {
    type: Buffer,
    required: true,
    select: false
  }
}, {
  _id: true,
  id: false
});

const communityContentSchema = new mongoose.Schema({
  type: {
    type: String,
    enum: ['recycle', 'event', 'tip', 'blog'],
    required: true,
    index: true
  },
  title: {
    type: String,
    required: true,
    trim: true
  },
  summary: {
    type: String,
    default: ''
  },
  body: {
    type: String,
    default: ''
  },
  imageUrl: {
    type: String,
    default: ''
  },
  sourceUrl: {
    type: String,
    default: ''
  },
  images: {
    type: [galleryImageSchema],
    default: [],
    validate: {
      validator: (images) => {
        const totalBytes = images.reduce((sum, image) => sum + image.size, 0);
        return images.length <= 5 && totalBytes <= 8 * 1024 * 1024;
      },
      message: 'Content galleries may contain at most 5 images and 8 MB total.'
    }
  },
  status: {
    type: String,
    enum: ['draft', 'published'],
    default: 'draft',
    index: true
  },
  eventStartAt: {
    type: Date,
    default: null,
    required: function () {
      return this.type === 'event';
    }
  },
  eventEndAt: {
    type: Date,
    default: null,
    validate: {
      validator: function (value) {
        return !value || this.type !== 'event' || !this.eventStartAt || value >= this.eventStartAt;
      },
      message: 'Event end time must be after its start time.'
    }
  },
  location: {
    type: String,
    default: '',
    maxlength: 240,
    required: function () {
      return this.type === 'event';
    }
  },
  createdBy: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User'
  }
}, {
  timestamps: true
});

communityContentSchema.index({ type: 1, status: 1, updatedAt: -1 });

module.exports = connection.model('CommunityContent', communityContentSchema);
