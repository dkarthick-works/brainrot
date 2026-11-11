import { ClerkProvider } from '@clerk/react'
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@fontsource/space-grotesk/700.css'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import App from './App'
import { applyTheme, loadTheme } from './theme'
import './styles/tokens.css'
import './styles/app.css'
import './styles/play.css'
import './styles/freestyle.css'
import './styles/cards.css'
import './styles/settings.css'

applyTheme(loadTheme()) // before first paint, so a forced theme never flashes

// Page-load errors show "Try again" quickly instead of after several silent retries
const queryClient = new QueryClient({ defaultOptions: { queries: { retry: 1, staleTime: 30_000 } } })

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ClerkProvider publishableKey={import.meta.env.VITE_CLERK_PUBLISHABLE_KEY} appearance={{ variables: { colorPrimary: '#437c77' } }}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </ClerkProvider>
  </StrictMode>,
)
