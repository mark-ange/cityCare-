const express = require('express');
const cors = require('cors');
const dotenv = require('dotenv');
const mongoose = require('mongoose');
const path = require('path');
const connectDB = require('./config/db');
const { connectReportDB } = require('./config/reportDb');

// Load environment variables
dotenv.config();

const app = express();

// Enable CORS for Android Emulator, Localhost, Mobile, and Web/Desktop Dashboard
app.use(cors({
  origin: '*',
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization']
}));

// Body parsing Middleware
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// Health Check API
app.get('/api/health', (req, res) => {
  const databaseOnline = mongoose.connection.readyState === 1;
  res.status(databaseOnline ? 200 : 503).json({
    status: databaseOnline ? 'ONLINE' : 'DEGRADED',
    database: databaseOnline ? 'CONNECTED' : 'UNAVAILABLE',
    system: 'CityCare CDO Resident Portal API',
    timestamp: new Date().toISOString()
  });
});

// API Routes
app.use('/api/auth', require('./routes/authRoutes'));
app.use('/api/reports', require('./routes/reportRoutes'));
app.use('/api/schedules', require('./routes/scheduleRoutes'));
app.use('/api/notifications', require('./routes/notificationRoutes'));
app.use('/api/content', require('./routes/contentRoutes'));

// Root endpoint
app.get('/', (req, res) => {
  res.send('CityCare CDO Node.js / Express API Backend is Running...');
});

// Port configuration
const PORT = process.env.PORT || 5001;

async function startServer() {
  await connectDB();
  await connectReportDB();

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`====================================================`);
    console.log(`🚀 CityCare Backend API Server running on port ${PORT}`);
    console.log(`📡 Local endpoint: http://localhost:${PORT}`);
    console.log(`📱 Android Emulator endpoint: http://10.0.2.2:${PORT}`);
    console.log(`====================================================`);
  });
}

startServer().catch((error) => {
  console.error(`Backend startup stopped because persistent storage is unavailable: ${error.message}`);
  process.exitCode = 1;
});
