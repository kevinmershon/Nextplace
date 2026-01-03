import { render, html, signal } from 'https://cdn.jsdelivr.net/npm/preact-htm-signals-standalone/dist/standalone.js';

const API_URL = 'http://localhost:8888/graphql';

async function graphqlMutation(mutation, variables = {}) {
  const response = await fetch(API_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query: mutation, variables })
  });
  const { data, errors } = await response.json();
  if (errors) throw new Error(errors[0].message);
  return data;
}

const email = signal('');
const submitting = signal(false);
const submitted = signal(false);
const error = signal(null);

async function handleSignup(e) {
  e.preventDefault();
  if (!email.value || submitting.value) return;

  submitting.value = true;
  error.value = null;

  try {
    const mutation = `
      mutation UserSignup($email: String!) {
        user_signup(email: $email) {
          id
          email
          signedUpAt
        }
      }
    `;

    await graphqlMutation(mutation, { email: email.value });
    submitted.value = true;
  } catch (err) {
    error.value = err.message || 'Failed to sign up. Please try again.';
    submitting.value = false;
  }
}

function LandingPage() {
  return html`
    <div class="landing">
      <section class="hero">
        <img
          class="hero-image"
          src="/images/road_trip_hero_banner.png"
          alt="Road trip adventure"
        />
        <div class="hero-content">
          <h1>Nextplace</h1>
          <p>Something to do. Somewhere to go.</p>
        </div>
      </section>

      <section class="signup-section">
        <h2>Join the Waitlist</h2>
        ${submitted.value ? html`
          <div class="success-message">
            <span class="material-icons" style="vertical-align: middle;">check_circle</span>
            Thanks for signing up! We'll be in touch soon.
          </div>
        ` : html`
          <form class="signup-form" onSubmit=${handleSignup}>
            <div class="form-group">
              <label for="email">Email Address</label>
              <input
                id="email"
                type="email"
                placeholder="you@example.com"
                value=${email}
                onInput=${(e) => email.value = e.target.value}
                required
                disabled=${submitting.value}
              />
            </div>
            <button
              type="submit"
              class="submit-button"
              disabled=${submitting.value}
            >
              ${submitting.value ? html`
                <span>Signing up...</span>
              ` : html`
                <span>Get Early Access</span>
                <span class="material-icons">arrow_forward</span>
              `}
            </button>
            ${error.value && html`
              <div class="error-message">${error.value}</div>
            `}
          </form>
        `}
      </section>

      <section class="features">
        <h2>How It Works</h2>
        <div class="feature-grid">
          <div class="feature-card">
            <div class="feature-icon">
              <span class="material-icons" style="font-size: inherit;">explore</span>
            </div>
            <h3>Spontaneous Discovery</h3>
            <p>Get personalized suggestions for places and activities when you're ready to go.</p>
          </div>
          <div class="feature-card">
            <div class="feature-icon">
              <span class="material-icons" style="font-size: inherit;">wb_sunny</span>
            </div>
            <h3>Weather Escapes</h3>
            <p>Find better weather within driving distance when you need a change of scenery.</p>
          </div>
          <div class="feature-card">
            <div class="feature-icon">
              <span class="material-icons" style="font-size: inherit;">groups</span>
            </div>
            <h3>Small-Group Encounters</h3>
            <p>Optional low-pressure social meetups anchored to activities you enjoy.</p>
          </div>
        </div>
      </section>
    </div>
  `;
}

render(html`<${LandingPage} />`, document.getElementById('app'));
