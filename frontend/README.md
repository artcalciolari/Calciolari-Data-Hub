# Frontend (Fase 4)

- React 19 + TypeScript + Vite 8, mobile-first PT-BR.
- Rotas: `/` Resumo, `/imports`, `/products`, `/products/:id`, `/sales`, `/sales/:id`.
- Decimais via `decimal.js`; formatação PT-BR com `Intl` (sem aritmética com `number`).
- A navegação inferior aparece em viewports móveis; desktop usa nav superior.

## Comandos

```bash
npm install
npm run dev        # Vite, proxy /api -> http://localhost:8080
npm run test       # Vitest
npm run typecheck  # tsc -b
npm run build
npx playwright test
```

Para E2E, deixe o backend rodando em `http://127.0.0.1:8080` com fixtures A/B importados.
