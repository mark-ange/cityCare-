const express = require('express');
const router = express.Router();
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const { OAuth2Client } = require('google-auth-library');
const User = require('../models/User');
const { protect } = require('../middleware/authMiddleware');

const GOOGLE_CLIENT_ID = process.env.GOOGLE_CLIENT_ID?.trim() ||
  '705374451507-oii7ck2c6nduelsjtkpu4240anniqhp7.apps.googleusercontent.com';
const googleClient = new OAuth2Client(GOOGLE_CLIENT_ID);

const generateToken = (id) => {
  return jwt.sign({ id }, process.env.JWT_SECRET || 'citycare_super_secret_jwt_key_2026', {
    expiresIn: '30d'
  });
};

// @route   POST /api/auth/register-resident
// @desc    Register a new resident user account (Mobile App Exclusive)
// @access  Public
router.post('/register-resident', async (req, res) => {
  try {
    const { fullName, phoneNumber, email, barangay, purokZone, password } = req.body;

    // 1. Client Field Validation
    if (!fullName || (!phoneNumber && !email) || !password) {
      return res.status(400).json({
        success: false,
        message: 'Validation Error: Full name, contact information (phone or email), and password are required.'
      });
    }

    if (password.length < 6) {
      return res.status(400).json({
        success: false,
        message: 'Validation Error: Password must be at least 6 characters long.'
      });
    }

    // 2. Duplicate Contact Information Check (409 Conflict)
    const existingQuery = [];
    if (phoneNumber && phoneNumber.trim()) existingQuery.push({ phoneNumber: phoneNumber.trim() });
    if (email && email.trim()) existingQuery.push({ email: email.trim().toLowerCase() });

    if (existingQuery.length > 0) {
      const existingUser = await User.findOne({ $or: existingQuery });
      if (existingUser) {
        return res.status(409).json({
          success: false,
          message: 'Conflict: An account with this phone number or email address is already registered.'
        });
      }
    }

    // 3. Create Resident Account (Enforce 'resident' role exclusively)
    const user = await User.create({
      fullName: fullName.trim(),
      phoneNumber: phoneNumber ? phoneNumber.trim() : `RES-${Date.now()}`,
      email: email ? email.trim().toLowerCase() : '',
      barangay: barangay ? barangay.trim() : 'Carmen',
      purokZone: purokZone ? purokZone.trim() : 'Zone 3',
      password,
      role: 'resident' // Strictly enforce resident role for mobile client registration
    });

    if (user) {
      const token = generateToken(user._id);
      return res.status(201).json({
        success: true,
        message: 'Resident account created successfully.',
        token,
        data: {
          _id: user._id,
          fullName: user.fullName,
          phoneNumber: user.phoneNumber,
          email: user.email,
          barangay: user.barangay,
          purokZone: user.purokZone,
          profilePhoto: user.profilePhoto,
          role: user.role,
          ecoPoints: user.ecoPoints,
          createdAt: user.createdAt
        }
      });
    } else {
      return res.status(400).json({
        success: false,
        message: 'Invalid user data provided.'
      });
    }
  } catch (error) {
    console.error('Registration Error:', error);
    return res.status(500).json({
      success: false,
      message: `Server Error: ${error.message}`
    });
  }
});

// @route   POST /api/auth/register
// @desc    Legacy registration endpoint (aliases to register-resident)
// @access  Public
router.post('/register', async (req, res) => {
  req.url = '/register-resident';
  return router.handle(req, res);
});

// @route   POST /api/auth/login
// @desc    Authenticate a resident using email/phone and password
// @access  Public
router.post('/login', async (req, res) => {
  try {
    const { identifier, password } = req.body; // identifier can be email or phone number

    if (!identifier || !password) {
      return res.status(400).json({
        success: false,
        message: 'Validation Error: Please provide email/phone and password.'
      });
    }

    const cleanId = identifier.trim().toLowerCase();
    let user = await User.findOne({
      $or: [{ email: cleanId }, { phoneNumber: identifier.trim() }]
    });

    const isMatch = user && await user.matchPassword(password);

    if (user && isMatch) {
      return res.json({
        success: true,
        message: 'Login successful',
        token: generateToken(user._id),
        data: {
          _id: user._id,
          fullName: user.fullName,
          phoneNumber: user.phoneNumber,
          email: user.email,
          barangay: user.barangay,
          purokZone: user.purokZone,
          profilePhoto: user.profilePhoto,
          role: user.role,
          ecoPoints: user.ecoPoints
        }
      });
    } else {
      return res.status(401).json({
        success: false,
        message: 'Unauthorized: Invalid email/phone or password.'
      });
    }
  } catch (error) {
    return res.status(500).json({ success: false, message: error.message });
  }
});

router.post('/google', async (req, res) => {
  try {
    const { idToken } = req.body;
    if (typeof idToken !== 'string' || !idToken.trim()) {
      return res.status(400).json({ success: false, message: 'Google ID token is required.' });
    }

    let profile;
    try {
      const ticket = await googleClient.verifyIdToken({
        idToken: idToken.trim(),
        audience: GOOGLE_CLIENT_ID
      });
      profile = ticket.getPayload();
    } catch {
      return res.status(401).json({ success: false, message: 'Google ID token is invalid or expired.' });
    }

    if (!profile?.sub || !profile.email || !profile.email_verified) {
      return res.status(401).json({ success: false, message: 'Google did not provide a verified email address.' });
    }

    const email = profile.email.trim().toLowerCase();
    let user = await User.findOne({ googleId: profile.sub });
    if (!user) {
      user = await User.findOne({ email });
      if (user?.googleId && user.googleId !== profile.sub) {
        return res.status(409).json({ success: false, message: 'This CityCare account is linked to another Google account.' });
      }
    }
    if (user && user.role !== 'resident') {
      return res.status(403).json({ success: false, message: 'Google sign-in is only available for resident accounts.' });
    }

    if (!user) {
      user = new User({
        fullName: profile.name || email.split('@')[0],
        phoneNumber: `GOOG-${profile.sub}`,
        email,
        googleId: profile.sub,
        profilePhoto: profile.picture || '',
        password: crypto.randomBytes(32).toString('hex'),
        role: 'resident'
      });
    } else {
      user.googleId = profile.sub;
      if (!user.profilePhoto && profile.picture) user.profilePhoto = profile.picture;
    }

    await user.save();
    return res.json({
      success: true,
      message: 'Google sign-in successful.',
      token: generateToken(user._id),
      data: {
        _id: user._id,
        fullName: user.fullName,
        phoneNumber: user.phoneNumber,
        email: user.email,
        barangay: user.barangay,
        purokZone: user.purokZone,
        profilePhoto: user.profilePhoto,
        role: user.role,
        ecoPoints: user.ecoPoints
      }
    });
  } catch (error) {
    console.error('Google Sign-In Error:', error);
    return res.status(500).json({ success: false, message: 'Google sign-in failed.' });
  }
});

router.post('/facebook', async (req, res) => {
  try {
    const { accessToken } = req.body;
    const appId = process.env.FACEBOOK_APP_ID?.trim();
    const appSecret = process.env.FACEBOOK_APP_SECRET?.trim();
    const hasPlaceholderCredential = [appId, appSecret].some(value =>
      value && /^(your-|replace-|keep-this-secret|<.*>)|example|placeholder/i.test(value)
    );
    if (!appId || !appSecret || hasPlaceholderCredential) {
      return res.status(503).json({
        success: false,
        message: 'Facebook sign-in is unavailable: set valid FACEBOOK_APP_ID and FACEBOOK_APP_SECRET values in backend/.env, then restart the API server.'
      });
    }
    if (typeof accessToken !== 'string' || !accessToken.trim()) {
      return res.status(400).json({ success: false, message: 'Facebook access token is required.' });
    }

    const debugUrl = new URL('https://graph.facebook.com/debug_token');
    debugUrl.searchParams.set('input_token', accessToken);
    debugUrl.searchParams.set('access_token', `${appId}|${appSecret}`);
    const debugResponse = await fetch(debugUrl);
    const debugBody = await debugResponse.json();
    const tokenData = debugBody.data;

    if (!debugResponse.ok || !tokenData?.is_valid || tokenData.app_id !== appId) {
      return res.status(401).json({ success: false, message: 'Facebook access token is invalid.' });
    }

    const profileUrl = new URL('https://graph.facebook.com/me');
    profileUrl.searchParams.set('fields', 'id,name,email');
    profileUrl.searchParams.set('access_token', accessToken);
    const profileResponse = await fetch(profileUrl);
    const profile = await profileResponse.json();
    if (!profileResponse.ok || !profile.id || profile.id !== tokenData.user_id) {
      return res.status(401).json({ success: false, message: 'Could not verify the Facebook account.' });
    }

    const email = typeof profile.email === 'string' ? profile.email.trim().toLowerCase() : '';
    let user = await User.findOne({ facebookId: profile.id });
    if (!user && email) {
      user = await User.findOne({ email });
      if (user) user.facebookId = profile.id;
    }

    if (!user) {
      user = new User({
        fullName: profile.name || 'Facebook Resident',
        phoneNumber: `FB-${profile.id}`,
        email: email || `facebook_${profile.id}@facebook.citycare.invalid`,
        facebookId: profile.id,
        password: crypto.randomBytes(32).toString('hex'),
        role: 'resident'
      });
    } else if (!user.facebookId) {
      user.facebookId = profile.id;
    }

    if (user.role !== 'resident') {
      return res.status(403).json({ success: false, message: 'Facebook sign-in is only available for resident accounts.' });
    }

    await user.save();
    return res.json({
      success: true,
      message: 'Facebook sign-in successful.',
      token: generateToken(user._id),
      data: {
        _id: user._id,
        fullName: user.fullName,
        phoneNumber: user.phoneNumber,
        email: user.email,
        barangay: user.barangay,
        purokZone: user.purokZone,
        profilePhoto: user.profilePhoto,
        role: user.role,
        ecoPoints: user.ecoPoints
      }
    });
  } catch (error) {
    console.error('Facebook Sign-In Error:', error);
    return res.status(500).json({ success: false, message: 'Facebook sign-in failed.' });
  }
});

// @route   GET /api/auth/profile
// @desc    Get user profile
// @access  Private
router.get('/profile', protect, async (req, res) => {
  try {
    const user = await User.findById(req.user._id).select('-password');
    if (user) {
      return res.json({ success: true, data: user });
    } else {
      return res.status(404).json({ success: false, message: 'User not found' });
    }
  } catch (error) {
    return res.status(500).json({ success: false, message: error.message });
  }
});

router.put('/profile', protect, async (req, res) => {
  try {
    const { fullName, phoneNumber, email, barangay, purokZone, profilePhoto } = req.body;
    const updates = {};

    if (fullName !== undefined) {
      if (!fullName.trim()) {
        return res.status(400).json({ success: false, message: 'Full name is required.' });
      }
      updates.fullName = fullName.trim();
    }

    if (phoneNumber !== undefined) {
      if (!phoneNumber.trim()) {
        return res.status(400).json({ success: false, message: 'Phone number is required.' });
      }
      const duplicatePhone = await User.findOne({
        phoneNumber: phoneNumber.trim(),
        _id: { $ne: req.user._id }
      });
      if (duplicatePhone) {
        return res.status(409).json({ success: false, message: 'That phone number is already in use.' });
      }
      updates.phoneNumber = phoneNumber.trim();
    }

    if (email !== undefined) {
      const cleanEmail = email.trim().toLowerCase();
      if (cleanEmail) {
        const duplicateEmail = await User.findOne({
          email: cleanEmail,
          _id: { $ne: req.user._id }
        });
        if (duplicateEmail) {
          return res.status(409).json({ success: false, message: 'That email address is already in use.' });
        }
      }
      updates.email = cleanEmail;
    }

    if (barangay !== undefined) updates.barangay = barangay.trim();
    if (purokZone !== undefined) updates.purokZone = purokZone.trim();
    if (profilePhoto !== undefined) updates.profilePhoto = profilePhoto;

    const user = await User.findByIdAndUpdate(
      req.user._id,
      { $set: updates },
      { new: true, runValidators: true }
    ).select('-password');

    if (!user) {
      return res.status(404).json({ success: false, message: 'User not found.' });
    }

    return res.json({ success: true, data: user });
  } catch (error) {
    console.error('Profile Update Error:', error);
    return res.status(500).json({ success: false, message: error.message });
  }
});

router.put('/change-password', protect, async (req, res) => {
  try {
    const { currentPassword, newPassword } = req.body;
    if (!currentPassword || !newPassword || newPassword.length < 6) {
      return res.status(400).json({
        success: false,
        message: 'Enter your current password and a new password with at least 6 characters.'
      });
    }

    const user = await User.findById(req.user._id);
    if (!user || !(await user.matchPassword(currentPassword))) {
      return res.status(400).json({ success: false, message: 'Current password is incorrect.' });
    }

    user.password = newPassword;
    await user.save();
    return res.json({ success: true, message: 'Password changed successfully.' });
  } catch (error) {
    console.error('Password Change Error:', error);
    return res.status(500).json({ success: false, message: error.message });
  }
});

module.exports = router;
