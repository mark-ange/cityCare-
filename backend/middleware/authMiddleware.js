const jwt = require('jsonwebtoken');
const User = require('../models/User');

const protect = async (req, res, next) => {
  let token;

  if (req.headers.authorization && req.headers.authorization.startsWith('Bearer')) {
    try {
      token = req.headers.authorization.split(' ')[1];
      const decoded = jwt.verify(token, process.env.JWT_SECRET || 'citycare_super_secret_jwt_key_2026');
      req.user = await User.findById(decoded.id).select('-password');
      return next();
    } catch (error) {
      console.error('JWT Token Verification Failed:', error.message);
      return res.status(401).json({ success: false, message: 'Not authorized, token failed' });
    }
  }

  if (!token) {
    return res.status(401).json({ success: false, message: 'Not authorized, no token provided' });
  }
};

const adminOnly = (req, res, next) => {
  if (req.user && (req.user.role === 'admin' || req.user.role === 'clenro_officer')) {
    next();
  } else {
    res.status(403).json({ success: false, message: 'Access denied: Admin or CLENRO Officer rights required' });
  }
};

module.exports = { protect, adminOnly };
