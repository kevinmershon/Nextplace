import { render, html, signal } from 'https://cdn.jsdelivr.net/npm/preact-htm-signals-standalone/dist/standalone.js';

const API_URL = 'http://localhost:8888/graphql';

async function graphqlQuery(query, variables = {}) {
  const response = await fetch(API_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query, variables })
  });
  const { data, errors } = await response.json();
  if (errors) throw new Error(errors[0].message);
  return data;
}

const currentView = signal('home');

function App() {
  return html`
    <div class="app">
      <header class="header">
        <h1>Nextplace</h1>
      </header>
      <main class="main">
        ${currentView.value === 'home' && html`<${Home} />`}
      </main>
    </div>
  `;
}

function Home() {
  return html`
    <div class="home">
      <h2>Where next?</h2>
      <p>Loading suggestion...</p>
    </div>
  `;
}

render(html`<${App} />`, document.getElementById('app'));
