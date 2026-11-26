/**
 * Audit module: append-only record of security- and money-relevant actions (who, what, when, before/after).
 * Fed through application events so business modules do not depend on it. Never stores secrets.
 */
package com.cryptotrading.audit;
