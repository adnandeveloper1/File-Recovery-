const allowedStates = new Set([
  'SUBSCRIPTION_STATE_ACTIVE', 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD', 'SUBSCRIPTION_STATE_CANCELED',
]);
const denied = () => ({ verified: false, acknowledged: false, productId: '', expiresAtMillis: 0 });

export function entitlement(purchase, productIds, now = Date.now()) {
  if (!allowedStates.has(purchase?.subscriptionState)) return null;
  return (purchase.lineItems ?? [])
    .filter(item => productIds.has(item.productId) && Number.isFinite(Date.parse(item.expiryTime)) && Date.parse(item.expiryTime) > now)
    .map(item => ({ productId: item.productId, expiresAtMillis: Date.parse(item.expiryTime) }))
    .sort((a, b) => b.expiresAtMillis - a.expiresAtMillis)[0] ?? null;
}

// packageName and allowed product IDs are server configuration, never client claims.
export async function verifyPurchase({ token, play, productIds, now = Date.now() }) {
  if (typeof token !== 'string' || token.length < 10 || token.length > 8192 || /[\s\x00-\x1f]/.test(token)) {
    const error = new Error('Invalid request'); error.status = 400; throw error;
  }
  let purchase = await play.get(token);
  let access = entitlement(purchase, productIds, now);
  if (!access) return denied();
  if (purchase.acknowledgementState !== 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED') {
    if (purchase.acknowledgementState !== 'ACKNOWLEDGEMENT_STATE_PENDING') return denied();
    try { await play.acknowledge(token, access.productId); }
    catch (error) {
      // Another request can acknowledge concurrently. Re-read Play before deciding.
      purchase = await play.get(token);
      if (purchase.acknowledgementState !== 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED') throw error;
    }
    purchase = await play.get(token);
    access = entitlement(purchase, productIds, now);
  }
  if (!access || purchase.acknowledgementState !== 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED') return denied();
  return { verified: true, acknowledged: true, ...access };
}
