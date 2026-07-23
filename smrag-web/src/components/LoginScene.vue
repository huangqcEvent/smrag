<template>
  <div
    class="login-scene"
    :class="`login-scene--${language}`"
    :data-language="language"
    :aria-hidden="interactive ? undefined : 'true'"
    :inert="!interactive"
  >
    <div class="scene-pattern" aria-hidden="true">
      <span v-for="item in patternItems" :key="item">SMRAG</span>
    </div>

    <header class="scene-brand">
      <span class="brand-mark">S</span>
      <span class="brand-name">{{ copy.brand }}</span>
    </header>

    <main class="scene-hero">
      <p class="hero-eyebrow">{{ copy.eyebrow }}</p>
      <h1>{{ copy.title }}</h1>
      <p class="hero-supporting">{{ copy.supporting }}</p>
    </main>

    <component
      :is="interactive ? 'form' : 'div'"
      class="login-panel"
      @submit.prevent="interactive && emit('submit')"
    >
      <div class="panel-heading">
        <div>
          <p class="panel-kicker">SMRAG / ACCESS</p>
          <h2>{{ copy.panelTitle }}</h2>
        </div>
        <span class="panel-index">01</span>
      </div>
      <p class="panel-hint">{{ copy.panelHint }}</p>

      <div class="form-field" :class="{ 'is-focused': activeField === 'username' }">
        <label :for="interactive ? 'login-username' : undefined">
          {{ copy.account }}
        </label>
        <div class="input-shell">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="8" r="4" />
            <path d="M4.5 21a7.5 7.5 0 0 1 15 0" />
          </svg>
          <input
            :id="interactive ? 'login-username' : undefined"
            :value="username"
            type="text"
            :name="interactive ? 'username' : undefined"
            :placeholder="copy.accountPlaceholder"
            :autocomplete="interactive ? 'username' : 'off'"
            :readonly="!interactive"
            :tabindex="interactive ? 0 : -1"
            @input="interactive && emit('update:username', $event.target.value)"
            @focus="interactive && emit('focus-field', 'username')"
            @blur="interactive && emit('focus-field', '')"
          />
        </div>
      </div>

      <div class="form-field" :class="{ 'is-focused': activeField === 'password' }">
        <label :for="interactive ? 'login-password' : undefined">
          {{ copy.password }}
        </label>
        <div class="input-shell">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <rect x="4" y="10" width="16" height="11" rx="2" />
            <path d="M8 10V7a4 4 0 0 1 8 0v3" />
          </svg>
          <input
            :id="interactive ? 'login-password' : undefined"
            :value="password"
            :type="showPassword ? 'text' : 'password'"
            :name="interactive ? 'password' : undefined"
            :placeholder="copy.passwordPlaceholder"
            :autocomplete="interactive ? 'current-password' : 'off'"
            :readonly="!interactive"
            :tabindex="interactive ? 0 : -1"
            @input="interactive && emit('update:password', $event.target.value)"
            @focus="interactive && emit('focus-field', 'password')"
            @blur="interactive && emit('focus-field', '')"
          />
          <component
            :is="interactive ? 'button' : 'span'"
            class="password-toggle"
            :type="interactive ? 'button' : undefined"
            :tabindex="interactive ? 0 : -1"
            :aria-label="interactive
              ? (showPassword ? copy.hidePassword : copy.showPassword)
              : undefined"
            @click="interactive && emit('toggle-password')"
          >
            <svg v-if="showPassword" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M3 12s3.5-6 9-6 9 6 9 6-3.5 6-9 6-9-6-9-6Z" />
              <circle cx="12" cy="12" r="2.5" />
            </svg>
            <svg v-else viewBox="0 0 24 24" aria-hidden="true">
              <path d="m4 4 16 16M10.6 6.2A8.2 8.2 0 0 1 12 6c5.5 0 9 6 9 6a13.8 13.8 0 0 1-2.2 3M8.4 8.4C5.1 10 3 12 3 12s3.5 6 9 6c1 0 1.9-.2 2.7-.5" />
            </svg>
          </component>
        </div>
      </div>

      <component
        :is="interactive ? 'button' : 'span'"
        class="login-submit"
        :type="interactive ? 'submit' : undefined"
        :disabled="interactive ? loading : undefined"
        :tabindex="interactive ? 0 : -1"
      >
        <span v-if="loading" class="submit-loading" aria-hidden="true">
          <i></i><i></i><i></i>
        </span>
        <span>{{ loading ? copy.loading : copy.submit }}</span>
        <svg v-if="!loading" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M5 12h14M14 7l5 5-5 5" />
        </svg>
      </component>

      <p class="panel-footer">
        <span class="status-dot"></span>
        {{ copy.footer }}
      </p>
    </component>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { LOGIN_COPY } from '@/constants/loginCopy'

const props = defineProps({
  language: {
    type: String,
    required: true,
    validator: value => ['zh', 'en'].includes(value)
  },
  interactive: {
    type: Boolean,
    default: false
  },
  username: {
    type: String,
    default: ''
  },
  password: {
    type: String,
    default: ''
  },
  showPassword: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  },
  activeField: {
    type: String,
    default: ''
  }
})

const emit = defineEmits([
  'update:username',
  'update:password',
  'toggle-password',
  'focus-field',
  'submit'
])

const copy = computed(() => LOGIN_COPY[props.language])
const patternItems = Array.from({ length: 30 }, (_, index) => index)
</script>

<style scoped>
.login-scene {
  --scene-bg: oklch(0.975 0.006 165);
  --scene-surface: oklch(0.99 0.005 165);
  --scene-text: oklch(0.19 0.018 170);
  --scene-muted: oklch(0.48 0.018 170);
  --scene-line: oklch(0.83 0.012 165);
  --scene-pattern: oklch(0.9 0.008 165 / 0.68);
  --scene-accent: oklch(0.47 0.085 184);
  --scene-accent-hover: oklch(0.4 0.078 184);
  --scene-accent-text: oklch(0.975 0.006 165);
  position: absolute;
  inset: 0;
  overflow: hidden;
  color: var(--scene-text);
  background: var(--scene-bg);
}

.login-scene--en {
  --scene-bg: oklch(0.155 0.015 170);
  --scene-surface: oklch(0.2 0.018 170);
  --scene-text: oklch(0.965 0.008 165);
  --scene-muted: oklch(0.72 0.014 165);
  --scene-line: oklch(0.34 0.018 170);
  --scene-pattern: oklch(0.29 0.015 170 / 0.72);
  --scene-accent: oklch(0.77 0.09 178);
  --scene-accent-hover: oklch(0.82 0.085 178);
  --scene-accent-text: oklch(0.17 0.018 170);
}

.scene-pattern {
  position: absolute;
  inset: -4vh -3vw;
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  grid-template-rows: repeat(5, minmax(0, 1fr));
  align-items: center;
  justify-items: center;
  color: var(--scene-pattern);
  font-size: 1rem;
  font-weight: 760;
  letter-spacing: 0.24em;
  user-select: none;
}

.scene-brand {
  position: absolute;
  top: 2rem;
  left: 2.5rem;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.brand-mark {
  display: grid;
  width: 2.25rem;
  height: 2.25rem;
  place-items: center;
  border-radius: 0.45rem;
  color: var(--scene-accent-text);
  background: var(--scene-accent);
  font-size: 0.9rem;
  font-weight: 800;
}

.brand-name {
  font-size: 0.92rem;
  font-weight: 720;
  letter-spacing: 0.02em;
}

.scene-hero {
  position: absolute;
  top: 47%;
  left: 7vw;
  z-index: 1;
  width: min(56rem, 67vw);
  transform: translateY(-50%);
}

.hero-eyebrow,
.panel-kicker {
  margin: 0;
  color: var(--scene-muted);
  font-size: 0.72rem;
  font-weight: 750;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.scene-hero h1 {
  max-width: 13ch;
  margin: 1rem 0 1.25rem;
  font-size: 4.75rem;
  font-weight: 850;
  line-height: 1.02;
  letter-spacing: -0.06em;
}

.login-scene--en .scene-hero h1 {
  max-width: 12ch;
  letter-spacing: -0.055em;
}

.hero-supporting {
  max-width: 34rem;
  margin: 0;
  color: var(--scene-muted);
  font-size: 1rem;
  font-weight: 540;
  letter-spacing: 0.02em;
}

.login-scene--en .hero-supporting {
  font-size: 0.82rem;
  font-weight: 680;
  letter-spacing: 0.12em;
}

.login-panel {
  position: absolute;
  right: 5vw;
  bottom: 6vh;
  z-index: 3;
  display: flex;
  width: min(22.5rem, calc(100vw - 3rem));
  flex-direction: column;
  padding: 1.35rem 1.4rem 1.15rem;
  border: 1px solid var(--scene-line);
  border-radius: 0.65rem;
  background: var(--scene-surface);
  box-shadow: 0 1rem 3rem oklch(0.16 0.02 170 / 0.09);
}

.login-scene--en .login-panel {
  box-shadow: 0 1rem 3rem oklch(0.06 0.01 170 / 0.24);
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.panel-heading h2 {
  margin: 0.25rem 0 0;
  font-size: 1.35rem;
  line-height: 1.2;
  letter-spacing: -0.025em;
}

.panel-index {
  color: var(--scene-muted);
  font-family: var(--font-code);
  font-size: 0.75rem;
}

.panel-hint {
  margin: 0.45rem 0 1.1rem;
  color: var(--scene-muted);
  font-size: 0.78rem;
}

.form-field + .form-field {
  margin-top: 0.8rem;
}

.form-field label {
  display: block;
  margin-bottom: 0.35rem;
  color: var(--scene-muted);
  font-size: 0.72rem;
  font-weight: 680;
  letter-spacing: 0.06em;
}

.input-shell {
  position: relative;
  display: flex;
  height: 2.75rem;
  align-items: center;
  border: 1px solid var(--scene-line);
  border-radius: 0.45rem;
  background: var(--scene-bg);
  transition:
    border-color 160ms ease-out,
    box-shadow 160ms ease-out;
}

.input-shell > svg {
  width: 1rem;
  height: 1rem;
  margin-left: 0.85rem;
  flex: 0 0 auto;
  fill: none;
  stroke: var(--scene-muted);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
}

.form-field.is-focused .input-shell,
.input-shell:focus-within {
  border-color: var(--scene-accent);
  box-shadow: 0 0 0 3px color-mix(in oklch, var(--scene-accent) 17%, transparent);
}

.input-shell input {
  min-width: 0;
  height: 100%;
  flex: 1;
  padding: 0 0.8rem;
  border: 0;
  outline: 0;
  color: var(--scene-text);
  background: transparent;
  font-size: 0.86rem;
}

.input-shell input::placeholder {
  color: color-mix(in oklch, var(--scene-muted) 72%, transparent);
}

.password-toggle {
  display: grid;
  width: 2.5rem;
  height: 100%;
  flex: 0 0 auto;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 0.35rem;
  color: var(--scene-muted);
  background: transparent;
}

button.password-toggle {
  cursor: pointer;
}

button.password-toggle:hover {
  color: var(--scene-accent);
}

button.password-toggle:focus-visible {
  outline: 2px solid var(--scene-accent);
  outline-offset: -4px;
}

.password-toggle svg {
  width: 1.05rem;
  height: 1.05rem;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
}

.login-submit {
  display: flex;
  width: 100%;
  height: 2.85rem;
  align-items: center;
  justify-content: center;
  gap: 0.65rem;
  margin-top: 1rem;
  border: 1px solid var(--scene-accent);
  border-radius: 0.45rem;
  color: var(--scene-accent-text);
  background: var(--scene-accent);
  font-size: 0.78rem;
  font-weight: 760;
  letter-spacing: 0.08em;
  transition:
    background-color 150ms ease-out,
    border-color 150ms ease-out,
    transform 90ms ease-out;
}

button.login-submit {
  cursor: pointer;
}

button.login-submit:hover:not(:disabled) {
  border-color: var(--scene-accent-hover);
  background: var(--scene-accent-hover);
}

button.login-submit:active:not(:disabled) {
  transform: scale(0.985);
}

button.login-submit:focus-visible {
  outline: 3px solid color-mix(in oklch, var(--scene-accent) 24%, transparent);
  outline-offset: 2px;
}

button.login-submit:disabled {
  cursor: wait;
  opacity: 0.72;
}

.login-submit svg {
  width: 1rem;
  height: 1rem;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.submit-loading {
  display: flex;
  align-items: center;
  gap: 0.2rem;
}

.submit-loading i {
  width: 0.22rem;
  height: 0.22rem;
  border-radius: 50%;
  background: currentColor;
  animation: loading-pulse 900ms ease-in-out infinite;
}

.submit-loading i:nth-child(2) {
  animation-delay: 120ms;
}

.submit-loading i:nth-child(3) {
  animation-delay: 240ms;
}

.panel-footer {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin: 0.85rem 0 0;
  color: var(--scene-muted);
  font-size: 0.68rem;
}

.status-dot {
  width: 0.38rem;
  height: 0.38rem;
  border-radius: 50%;
  background: var(--scene-accent);
}

@keyframes loading-pulse {
  0%,
  100% {
    opacity: 0.4;
    transform: translateY(0);
  }
  50% {
    opacity: 1;
    transform: translateY(-0.16rem);
  }
}

@media (max-width: 1100px) {
  .scene-hero {
    top: 35%;
    width: 86vw;
  }

  .scene-hero h1 {
    font-size: 3.6rem;
  }
}

@media (max-width: 720px) {
  .scene-brand {
    top: 1.25rem;
    left: 1.25rem;
  }

  .scene-pattern {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    font-size: 0.78rem;
  }

  .scene-pattern span:nth-child(n + 16) {
    display: none;
  }

  .scene-hero {
    top: 7.2rem;
    left: 1.5rem;
    width: calc(100vw - 3rem);
    transform: none;
  }

  .scene-hero h1 {
    max-width: 12ch;
    margin: 0.7rem 0 0.8rem;
    font-size: 2.45rem;
    line-height: 1.08;
  }

  .hero-supporting {
    max-width: 28rem;
    font-size: 0.88rem;
  }

  .login-panel {
    right: 50%;
    bottom: 1.2rem;
    width: calc(100vw - 2.5rem);
    padding: 1.15rem 1.15rem 1rem;
    transform: translateX(50%);
  }
}

@media (max-height: 720px) and (min-width: 721px) {
  .scene-hero {
    top: 42%;
  }

  .scene-hero h1 {
    font-size: 3.5rem;
  }

  .login-panel {
    bottom: 1.5rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .submit-loading i {
    animation: none;
  }
}
</style>
