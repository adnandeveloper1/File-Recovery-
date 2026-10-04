import test from 'node:test';
import assert from 'node:assert/strict';
import { entitlement, verifyPurchase } from './verify.mjs';
const now = Date.parse('2026-10-04T00:00:00Z');
const productIds = new Set(['recovery_monthly', 'recovery_yearly']);
const valid = () => ({ subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE', acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED', lineItems: [{ productId: 'recovery_monthly', expiryTime: '2026-11-04T00:00:00Z' }] });
for (const state of ['ACTIVE', 'IN_GRACE_PERIOD', 'CANCELED']) test(state + ' keeps paid access until expiry', () => {
  assert.ok(entitlement({ ...valid(), subscriptionState: 'SUBSCRIPTION_STATE_' + state }, productIds, now));
});
for (const state of ['PENDING', 'PAUSED', 'ON_HOLD', 'EXPIRED', 'PENDING_PURCHASE_CANCELED', 'UNSPECIFIED']) test(state + ' is denied even with future timestamp', () => {
  assert.equal(entitlement({ ...valid(), subscriptionState: 'SUBSCRIPTION_STATE_' + state }, productIds, now), null);
});
test('foreign product, malformed expiry and exact expiry are denied', () => {
  for (const item of [{ productId: 'other', expiryTime: '2027-01-01' }, { productId: 'recovery_monthly', expiryTime: 'invalid' }, { productId: 'recovery_monthly', expiryTime: new Date(now).toISOString() }]) {
    assert.equal(entitlement({ ...valid(), lineItems: [item] }, productIds, now), null);
  }
});
test('acknowledgement must succeed before access is returned', async () => {
  let acknowledged = false;
  const play = {
    get: async () => ({ ...valid(), acknowledgementState: acknowledged ? 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED' : 'ACKNOWLEDGEMENT_STATE_PENDING' }),
    acknowledge: async (token, product) => { assert.equal(product, 'recovery_monthly'); acknowledged = true; },
  };
  assert.equal((await verifyPurchase({ token: 'test-token-long', play, productIds, now })).verified, true);
  assert.equal(acknowledged, true);
});
test('failed acknowledgement never grants access', async () => {
  const play = { get: async () => ({ ...valid(), acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING' }), acknowledge: async () => { throw new Error('Offline'); } };
  await assert.rejects(verifyPurchase({ token: 'test-token-long', play, productIds, now }), /Offline/);
});
test('invalid tokens fail before making a Google request', async () => {
  await assert.rejects(verifyPurchase({ token: '', play: { get() { assert.fail('Must not call Google'); } }, productIds, now }), /Invalid request/);
});
