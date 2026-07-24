<template>
  <div class="login-scene">
    <div class="scene-pattern" aria-hidden="true">
      <span v-for="item in patternItems" :key="item">
        {{ copy.pattern[item % copy.pattern.length] }}
      </span>
    </div>

    <section class="scene-hero">
      <p class="hero-eyebrow">{{ copy.eyebrow }}</p>
      <h1>{{ copy.title }}</h1>
      <p class="hero-supporting">{{ copy.supporting }}</p>
    </section>

    <div
      class="login-hero-reveal"
      :class="{ 'is-active': lensActive }"
      aria-hidden="true"
      inert
    >
      <div class="scene-pattern">
        <span v-for="item in patternItems" :key="`reveal-${item}`">
          {{ englishCopy.pattern[item % englishCopy.pattern.length] }}
        </span>
      </div>
      <section class="scene-hero">
        <p class="hero-eyebrow">{{ copy.eyebrow }}</p>
        <h1>{{ englishCopy.title }}</h1>
        <p class="hero-supporting">{{ copy.supporting }}</p>
      </section>
    </div>

    <form
      class="login-panel"
      @pointerenter="handleLensBlock($event, true)"
      @pointerleave="handleLensBlock($event, false)"
      @submit.prevent="emit('submit')"
    >
      <div class="panel-heading">
        <div>
          <p class="panel-kicker">{{ copy.panelKicker }}</p>
          <h2>{{ copy.panelTitle }}</h2>
        </div>
        <span class="panel-index">01</span>
      </div>
      <p class="panel-hint">{{ copy.panelHint }}</p>

      <div class="form-field" :class="{ 'is-focused': activeField === 'username' }">
        <label for="login-username">
          {{ copy.account }}
        </label>
        <div class="input-shell">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="8" r="4" />
            <path d="M4.5 21a7.5 7.5 0 0 1 15 0" />
          </svg>
          <input
            id="login-username"
            :value="username"
            type="text"
            name="username"
            :placeholder="copy.accountPlaceholder"
            autocomplete="username"
            @input="emit('update:username', $event.target.value)"
            @focus="emit('focus-field', 'username')"
            @blur="emit('focus-field', '')"
          />
        </div>
      </div>

      <div class="form-field" :class="{ 'is-focused': activeField === 'password' }">
        <label for="login-password">
          {{ copy.password }}
        </label>
        <div class="input-shell">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <rect x="4" y="10" width="16" height="11" rx="2" />
            <path d="M8 10V7a4 4 0 0 1 8 0v3" />
          </svg>
          <input
            id="login-password"
            :value="password"
            :type="showPassword ? 'text' : 'password'"
            name="password"
            :placeholder="copy.passwordPlaceholder"
            autocomplete="current-password"
            @input="emit('update:password', $event.target.value)"
            @focus="emit('focus-field', 'password')"
            @blur="emit('focus-field', '')"
          />
          <button
            class="password-toggle"
            type="button"
            :aria-label="showPassword ? copy.hidePassword : copy.showPassword"
            @click="emit('toggle-password')"
          >
            <svg v-if="showPassword" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M3 12s3.5-6 9-6 9 6 9 6-3.5 6-9 6-9-6-9-6Z" />
              <circle cx="12" cy="12" r="2.5" />
            </svg>
            <svg v-else viewBox="0 0 24 24" aria-hidden="true">
              <path d="m4 4 16 16M10.6 6.2A8.2 8.2 0 0 1 12 6c5.5 0 9 6 9 6a13.8 13.8 0 0 1-2.2 3M8.4 8.4C5.1 10 3 12 3 12s3.5 6 9 6c1 0 1.9-.2 2.7-.5" />
            </svg>
          </button>
        </div>
      </div>

      <button
        class="login-submit"
        type="submit"
        :disabled="loading"
      >
        <span v-if="loading" class="submit-loading" aria-hidden="true">
          <i></i><i></i><i></i>
        </span>
        <span>{{ loading ? copy.loading : copy.submit }}</span>
        <svg v-if="!loading" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M5 12h14M14 7l5 5-5 5" />
        </svg>
      </button>

      <p class="panel-footer">
        <span class="status-dot"></span>
        {{ copy.footer }}
      </p>
    </form>
  </div>
</template>

<script setup>
import { LOGIN_COPY } from '@/constants/loginCopy'
import { isMousePointer } from '@/utils/pointerLens'

defineProps({
  lensActive: {
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
  'lens-blocked',
  'submit'
])

const copy = LOGIN_COPY.zh
const englishCopy = LOGIN_COPY.en
const patternItems = Array.from({ length: 30 }, (_, index) => index)

const handleLensBlock = (event, blocked) => {
  if (isMousePointer(event)) emit('lens-blocked', blocked)
}
</script>

<style scoped>
.login-scene {
  --scene-bg: oklch(0.992 0.002 165);
  --scene-surface: oklch(0.985 0.003 165);
  --scene-text: oklch(0.16 0.01 170);
  --scene-muted: oklch(0.48 0.018 170);
  --scene-line: oklch(0.83 0.012 165);
  --scene-pattern: oklch(0.92 0.004 165 / 0.72);
  --scene-accent: oklch(0.47 0.085 184);
  --scene-accent-hover: oklch(0.4 0.078 184);
  --scene-accent-text: oklch(0.975 0.006 165);
  position: absolute;
  inset: 0;
  overflow: hidden;
  color: var(--scene-text);
  background: var(--scene-bg);
}

.login-hero-reveal {
  --scene-bg: oklch(0.13 0.008 170);
  --scene-text: oklch(0.965 0.008 165);
  --scene-muted: oklch(0.72 0.014 165);
  --scene-pattern: oklch(0.9 0.006 165 / 0.72);
  position: absolute;
  inset: 0;
  z-index: 2;
  overflow: hidden;
  color: var(--scene-text);
  background: var(--scene-bg);
  pointer-events: none;
  clip-path: circle(0 at var(--lens-x) var(--lens-y));
  transition: clip-path 180ms cubic-bezier(0.22, 1, 0.36, 1);
  will-change: clip-path;
}

.login-hero-reveal.is-active {
  clip-path: circle(var(--lens-radius) at var(--lens-x) var(--lens-y));
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
  letter-spacing: 0.14em;
  user-select: none;
}

.scene-pattern span {
  max-width: 12.5rem;
  text-align: center;
  line-height: 1.35;
}

.scene-hero {
  position: absolute;
  top: 47%;
  left: 7vw;
  z-index: 1;
  width: min(56rem, 67vw);
  transform: translateY(-50%);
  isolation: isolate;
}

.scene-hero::before {
  content: '';
  position: absolute;
  inset: -2.5rem -3rem;
  z-index: -1;
  border-radius: 50%;
  background: var(--scene-bg);
  box-shadow: 0 0 2.5rem 2.5rem var(--scene-bg);
  pointer-events: none;
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
  max-width: none;
  margin: 1rem 0 1.25rem;
  font-size: 4.75rem;
  font-weight: 850;
  line-height: 1.02;
  letter-spacing: -0.06em;
  white-space: nowrap;
}

.hero-supporting {
  max-width: 34rem;
  margin: 0;
  color: var(--scene-muted);
  font-size: 1rem;
  font-weight: 540;
  letter-spacing: 0.02em;
}

.login-panel {
  position: absolute;
  top: 50%;
  right: clamp(2rem, 6vw, 7rem);
  z-index: 3;
  display: flex;
  width: min(26rem, calc(100vw - 3rem));
  flex-direction: column;
  padding: 1.75rem 1.8rem 1.45rem;
  border: 1px solid var(--scene-line);
  border-radius: 0.65rem;
  background: var(--scene-surface);
  box-shadow: 0 1rem 3rem oklch(0.16 0.02 170 / 0.09);
  transform: translateY(-50%);
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
  height: 3rem;
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
  height: 3.1rem;
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

@media (max-width: 1280px) {
  .scene-hero {
    left: 5vw;
    width: 48vw;
  }

  .scene-hero h1 {
    max-width: 11ch;
    font-size: 3.6rem;
    white-space: normal;
  }
}

@media (max-width: 900px) {
  .scene-hero {
    top: 4rem;
    left: 3rem;
    width: calc(100vw - 6rem);
    transform: none;
  }

  .scene-hero h1 {
    max-width: none;
    margin: 0.8rem 0 0.9rem;
    font-size: 3rem;
    white-space: nowrap;
  }

  .login-panel {
    top: 21rem;
    right: 50%;
    width: min(26rem, calc(100vw - 3rem));
    transform: translateX(50%);
  }
}

@media (max-width: 720px) {
  .login-hero-reveal {
    display: none;
  }

  .scene-pattern {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    font-size: 0.78rem;
  }

  .scene-pattern span:nth-child(n + 16) {
    display: none;
  }

  .scene-hero {
    top: 3rem;
    left: 1.5rem;
    width: calc(100vw - 3rem);
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
    top: 17.5rem;
    width: calc(100vw - 2.5rem);
    padding: 1.4rem 1.3rem 1.2rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-hero-reveal {
    display: none;
  }

  .submit-loading i {
    animation: none;
  }
}
</style>
