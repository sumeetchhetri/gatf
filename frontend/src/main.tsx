import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import Shell from './Shell';
import './styles.css';

const el = document.getElementById('root');
if (el) {
  createRoot(el).render(
    <StrictMode>
      <Shell />
    </StrictMode>,
  );
}
