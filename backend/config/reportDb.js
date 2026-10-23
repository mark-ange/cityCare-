const mongoose = require('mongoose');

const connection = mongoose.createConnection();

async function connectReportDB() {
  if (!process.env.MONGO_URI) {
    throw new Error('MONGO_URI is required to connect report storage.');
  }

  if (connection.readyState === 1) return connection;

  await connection.openUri(process.env.MONGO_URI, {
    dbName: process.env.MONGO_REPORT_DATABASE || 'citycare_db',
    serverSelectionTimeoutMS: 10000
  });

  console.log(`Shared report database connected: ${connection.name}`);
  return connection;
}

module.exports = { connection, connectReportDB };
