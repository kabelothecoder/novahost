<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { verifySubscription } from '../services/authService'

const router = useRouter()
const email = ref('')
const isLoading = ref(false)
const errorMessage = ref('')

const handleUnlock = async () => {
  errorMessage.value = ''
  if (!email.value) {
    errorMessage.value = 'Please enter your email address.'
    return
  }

  isLoading.value = true
  const result = await verifySubscription(email.value)
  isLoading.value = false

  if (result.success) {
    // Navigate to the next appropriate view (e.g., home or parent setup)
    router.push({ name: 'home' })
  } else {
    errorMessage.value = result.message || 'Verification failed.'
  }
}
</script>

<template>
  <div class="unlock-container">
    <div class="unlock-card" v-motion-pop>
      <!-- Lock Icon -->
      <div class="icon-container">
        <svg class="lock-icon" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
        </svg>
      </div>

      <h1 class="headline">Unlock NovaHost</h1>
      <p class="subtitle">Enter your registered email to verify your subscription.</p>

      <form @submit.prevent="handleUnlock" class="form-container">
        <div class="input-wrapper">
          <input 
            type="email" 
            v-model="email" 
            placeholder="Email Address" 
            class="email-input"
            :disabled="isLoading"
          />
        </div>
        
        <div v-if="errorMessage" class="error-message">
          {{ errorMessage }}
        </div>

        <button type="submit" class="action-button" :disabled="isLoading">
          <span v-if="isLoading" class="spinner"></span>
          <span v-else>CHECK STATUS</span>
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.unlock-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #F4F7F9; /* Soft icy off-white */
  padding: 24px;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}

.unlock-card {
  background: #FFFFFF; /* Pure white */
  border-radius: 24px;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.06); /* Soft diffused shadow */
  width: 100%;
  max-width: 400px;
  padding: 48px 32px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.icon-container {
  width: 64px;
  height: 64px;
  background-color: rgba(92, 156, 230, 0.1);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
}

.lock-icon {
  width: 32px;
  height: 32px;
  color: #5C9CE6;
}

.headline {
  margin: 0;
  color: #1A1D20; /* Deep Charcoal */
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.5px;
  margin-bottom: 12px;
}

.subtitle {
  margin: 0;
  color: #6B7280; /* Soft Grey */
  font-size: 15px;
  line-height: 1.5;
  margin-bottom: 32px;
}

.form-container {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.input-wrapper {
  width: 100%;
}

.email-input {
  width: 100%;
  padding: 16px 20px;
  border-radius: 16px;
  border: 1px solid #E5E7EB;
  background-color: #F9FAFB;
  font-size: 16px;
  color: #1A1D20;
  outline: none;
  transition: all 0.2s ease;
  box-sizing: border-box;
}

.email-input:focus {
  border-color: #5C9CE6;
  background-color: #FFFFFF;
  box-shadow: 0 0 0 4px rgba(92, 156, 230, 0.1);
}

.email-input::placeholder {
  color: #9CA3AF;
}

.error-message {
  color: #EF4444;
  font-size: 14px;
  background-color: #FEF2F2;
  padding: 12px;
  border-radius: 12px;
  text-align: left;
}

.action-button {
  width: 100%;
  padding: 18px;
  border-radius: 9999px; /* Pill-shaped */
  background-color: #5C9CE6; /* Soft Light Blue */
  color: #FFFFFF;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.5px;
  border: none;
  cursor: pointer;
  outline: none;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 8px;
}

.action-button:hover:not(:disabled) {
  background-color: #4A89D3;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(92, 156, 230, 0.25);
}

.action-button:active:not(:disabled) {
  transform: translateY(1px);
  box-shadow: none;
}

.action-button:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

/* Simple CSS Spinner */
.spinner {
  display: inline-block;
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255,255,255,0.3);
  border-radius: 50%;
  border-top-color: #fff;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
