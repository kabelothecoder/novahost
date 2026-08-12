import { reactive } from 'vue'
import { supabase } from '../utils/supabaseClient'
import { secStorage } from '../utils/secStorage'

export const authState = reactive({
  isVerified: false,
  isVerifying: true, // true initially so we can block routes on load
  email: secStorage.getItem('VERIFIED_EMAIL') || null
})

/**
 * Re-verifies the locally stored email against Supabase.
 * Should be called on app initialization or when checking existing sessions.
 */
export async function initializeAuth() {
  if (authState.email) {
    authState.isVerifying = true
    const result = await verifySubscription(authState.email)
    if (result.success) {
      authState.isVerified = true
    } else {
      // If verification fails on reload, clear the email and state
      authState.isVerified = false
      authState.email = null
      secStorage.removeItem('VERIFIED_EMAIL')
    }
  }
  authState.isVerifying = false
}

/**
 * Checks Supabase for an active subscription for the given email.
 * @param {string} email 
 * @returns {Promise<{success: boolean, message?: string}>}
 */
export async function verifySubscription(email) {
  if (!email || typeof email !== 'string') {
    return { success: false, message: 'Please enter a valid email address.' }
  }

  try {
    const { data, error } = await supabase
      .from('subscriptions')
      .select('status')
      .eq('email', email.trim())
      .maybeSingle()

    if (error) {
      console.error('Supabase error during verification:', error)
      return { success: false, message: 'Network error. Please try again later.' }
    }

    if (!data) {
      return { success: false, message: 'No subscription found for this email address.' }
    }

    if (data.status === 'active') {
      authState.isVerified = true
      authState.email = email.trim()
      secStorage.setItem('VERIFIED_EMAIL', authState.email)
      return { success: true }
    } else {
      return { success: false, message: `Your subscription status is: ${data.status || 'inactive'}. An active subscription is required.` }
    }
  } catch (err) {
    console.error('Unexpected error verifying subscription:', err)
    return { success: false, message: 'An unexpected error occurred. Please try again later.' }
  }
}

export function logout() {
  authState.isVerified = false
  authState.email = null
  secStorage.removeItem('VERIFIED_EMAIL')
}
