/**
 * Trading module: builds trade proposals from signals, requests risk validation, manages the approval
 * workflow (expiry, binding approval to the exact proposal version) and hands approved proposals to
 * execution. Automatic execution is disabled by default and out of scope until explicitly authorized.
 */
package com.cryptotrading.trading;
