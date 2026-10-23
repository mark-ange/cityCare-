# CityCare CDO - Backend API Service

This is the Node.js / Express & MongoDB REST API server for **CityCare CDO** (System Architecture Tier 2: Application Tier & Tier 3: Data Tier).

---

## 🏗️ System Architecture Overview

```
+-----------------------------------------------------------------------+
|                       1. CLIENT TIER (User Interfaces)                |
|  +----------------------------+     +-------------------------------+ |
|  |       Web / Desktop        |     |       Kotlin Mobile App       | |
|  |     Dashboard (Admin)      |     |     (Community Residents)     | |
|  +--------------+-------------+     +---------------+---------------+ |
+-----------------|-----------------------------------|-----------------+
                  |                                   |
                  +-----------------+   +-------------+
                                    |   |
+-----------------------------------|---|-------------------------------+
|                       2. APPLICATION TIER (Server & Logic)            |
|  +-----------------------------------------------------------------+  |
|  |                   Node.js / Express API Server                   |  |
|  |            (JWT Authentication & Incident Processing)           |  |
|  +--------------------------------+--------------------------------+  |
+-----------------------------------|-----------------------------------+
                                    |
+-----------------------------------|-----------------------------------+
|                       3. DATA & EXTERNAL SERVICES                     |
|  +--------------------------------+--------------------------------+  |
|  |                       MongoDB Database                          |  |
|  |               (Users, Incident Reports, Schedules)              |  |
|  +-----------------------------------------------------------------+  |
+-----------------------------------------------------------------------+
```

---

## 📁 Project Structure

```
backend/
├── config/
│   └── db.js                 # MongoDB Connection Logic
├── middleware/
│   └── authMiddleware.js     # JWT Authorization & Admin Role Enforcement
├── models/
│   ├── User.js               # Resident & Admin User Model
│   ├── Report.js             # Waste Incident Report Model
│   ├── Schedule.js           # Garbage Collection Schedule Model
│   └── Notification.js       # Push Notification History Model
├── routes/
│   ├── authRoutes.js         # Register, Login, Profile endpoints
│   ├── reportRoutes.js       # Incident Report CRUD endpoints
│   ├── scheduleRoutes.js     # Waste Collection Schedule endpoints
│   └── notificationRoutes.js # Notification endpoints
├── .env                      # Environment Variables
├── .env.example              # Environment Configuration Template
├── package.json              # Node.js dependencies
├── README.md                 # Documentation & Connection Guide
└── server.js                 # Express Application Entry Point
```

---

## ⚡ Quick Start Guide

### 1. Prerequisites
- [Node.js](https://nodejs.org/) (v18 or higher)
- A [MongoDB Atlas](https://www.mongodb.com/atlas/database) cluster and a database user.

### 2. Configure durable cloud storage
1. Create a MongoDB Atlas cluster and a database user.
2. In Atlas **Network Access**, allow the IP address of the computer or host running this backend.
3. In **Connect → Drivers**, copy the Node.js connection string and replace the username, password, and cluster host placeholders.
4. Put the URI in `backend/.env` as `MONGO_URI=...`. Keep `.env` private; never put the URI in the Android app or commit it to Git.
5. Set `MONGO_REPORT_DATABASE=citycare_db` so resident reports, collection schedules, and published community content use the same MongoDB database that the web-admin API reads. This setting is separate from the database used for resident accounts and notifications.

The backend will not start unless both MongoDB connections succeed. Report submissions return success only after MongoDB confirms the write to the shared database. When the API or database is unavailable, the Android app keeps reports on the phone and retries; it does not mark those reports as delivered.

### 3. Installation
Open your terminal in the `backend/` folder:
```bash
cd backend
npm install
```

### 4. Running the Backend Server
Start in production mode:
```bash
npm start
```
Or start in development mode with auto-reload (Nodemon):
```bash
npm run dev
```

Server output:
```text
🚀 CityCare Backend API Server running on port 5001
📡 Local endpoint: http://localhost:5001
📱 Android Emulator endpoint: http://10.0.2.2:5001
MongoDB Connected: <Atlas cluster>
```

To accept new submissions while this computer is turned off, the API server must also be deployed to an always-on host and the Android app must use that host's HTTPS URL. Atlas keeps the database online, but it cannot receive requests when a locally running API server is off. Reports submitted while the API is unavailable remain queued on the phone for retry.

---

## 🔌 How to Connect Frontends to this Backend

### A. Connecting the Kotlin Android Mobile App
1. **Host Address on Android Emulator:**
   - In Android Emulator, `10.0.2.2` points directly to your computer's `localhost`.
   - Build the Android app with `-PCITYCARE_API_URL=http://10.0.2.2:5001/api`.

2. **Connecting from a Physical Android Phone:**
   - Find your local IP address (e.g., `192.168.1.15` via `ipconfig` on Windows or `ifconfig` on Mac/Linux).
   - In `CityCareApiService.kt`, change `BASE_URL` to:
     ```kotlin
     var BASE_URL = "http://<COMPUTER_LAN_IP>:5001/api"
     ```
   - Ensure your phone and PC are on the same Wi-Fi network.

3. **Sample Request from Kotlin Mobile App:**
   ```kotlin
   // Submit incident report to backend
   val isSuccess = CityCareApiService.uploadReportToCloud(reportData)
   ```

---

### B. Connecting the Desktop / Web Admin Dashboard
Whether your desktop backend/dashboard is built using React, Vue, Electron, Node, Python, or Java C# desktop framework:

1. **API Base URL:**
   ```javascript
   const API_BASE_URL = "http://localhost:5001/api";
   ```

2. **Fetching All Incident Reports for Admin Review:**
   ```javascript
   // GET http://localhost:5001/api/reports
   const response = await fetch("http://localhost:5001/api/reports");
   const data = await response.json();
   console.log("All Reports:", data.data);
   ```

3. **Updating Incident Status / Assigning Dispatch Driver:**
   ```javascript
   // PUT http://localhost:5001/api/reports/#CDO-2026-0412/status
   const response = await fetch("http://localhost:5001/api/reports/#CDO-2026-0412/status", {
     method: "PUT",
     headers: {
       "Content-Type": "application/json"
     },
     body: JSON.stringify({
       status: "ASSIGNED",
       unitAssigned: "CLENRO Truck #4"
     })
   });
   ```

---

## 📡 API Endpoints Summary

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/health` | Server Health & Online Status |
| `POST` | `/api/auth/register` | Register new resident account |
| `POST` | `/api/auth/login` | Login user & receive JWT token |
| `GET` | `/api/auth/profile` | Get current user profile (JWT protected) |
| `GET` | `/api/reports` | Get all incident waste reports |
| `POST` | `/api/reports` | Submit a new waste concern report |
| `PUT` | `/api/reports/:id/status` | Update report status (`PENDING`, `ASSIGNED`, `RESOLVED`) |
| `GET` | `/api/schedules` | Get collection schedule by barangay |
| `PUT` | `/api/schedules/:barangay` | Update a schedule (admin/CLENRO JWT); notifies residents of that barangay |
| `GET` | `/api/content?type=recycle\|event\|tip\|blog` | Get published community content |
| `POST` | `/api/content` | Publish community content (admin/CLENRO JWT); notifies residents |
| `GET` | `/api/notifications` | Get the signed-in user's notifications and read state |
| `PATCH` | `/api/notifications/:id/read` | Mark one of the signed-in user's notifications as read |
| `PATCH` | `/api/notifications/read-all` | Mark all of the signed-in user's notifications as read |

---

Report status changes (`ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, and `REJECTED`) create account-specific notifications. Schedule and content publication endpoints also create unread notices for affected residents. Notification reads are stored per account in MongoDB.
