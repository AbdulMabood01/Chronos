import { useEffect, useState } from 'react';

const fallbackMessage = 'Welcome to Chronos website - Maxwells time keeping app';

export default function App() {
  const [message, setMessage] = useState(fallbackMessage);
  const [status, setStatus] = useState('Connecting to backend...');

  useEffect(() => {
    fetch('/api/welcome')
      .then((response) => {
        if (!response.ok) {
          throw new Error('Backend returned an error');
        }

        return response.json();
      })
      .then((data) => {
        setMessage(data.message || fallbackMessage);
        setStatus('Connected to Spring Boot API');
      })
      .catch(() => {
        setMessage(fallbackMessage);
        setStatus('Backend is not running yet');
      });
  }, []);

  return (
    <main className="app-shell">
      <section className="card">
        <p className="eyebrow">Chronos</p>
        <h1>{message}</h1>
        <p className="subtitle">
          {status}
        </p>
      </section>
    </main>
  );
}
