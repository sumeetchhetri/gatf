import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import Shell from './Shell';
import { applyTheme, getTheme } from './theme';
import './styles.css';

applyTheme(getTheme()); // set data-theme before first paint to avoid a flash

const el = document.getElementById('root');
if (el) {
  createRoot(el).render(
    <StrictMode>
      <Shell />
    </StrictMode>,
  );
}
