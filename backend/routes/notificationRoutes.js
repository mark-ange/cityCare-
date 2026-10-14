const express = require('express');
const router = express.Router();

const mockNotifications = [
  {
    id: 'n1',
    title: 'Report #CDO-0412 Resolved',
    body: 'Your report in Zone 3 Carmen was marked RESOLVED.',
    time: '10 mins ago',
    type: 'REPORT_UPDATE'
  },
  {
    id: 'n2',
    title: 'Dispatch Truck En Route',
    body: 'CLENRO dispatched Truck #4 to your reported location in Lapasan.',
    time: '2 hours ago',
    type: 'DISPATCH'
  },
  {
    id: 'n3',
    title: 'Holiday Waste Schedule Reminder',
    body: 'Reminder: Schedule change for waste collection in Barangay Macasandig this holiday weekend.',
    time: 'Yesterday, 08:00 AM',
    type: 'COLLECTION_SCHEDULE'
  }
];

// @route   GET /api/notifications
// @desc    Get push notification history
// @access  Public
router.get('/', (req, res) => {
  res.json({
    success: true,
    data: mockNotifications
  });
});

module.exports = router;
