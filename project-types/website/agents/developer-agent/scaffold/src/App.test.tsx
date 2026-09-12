import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import App from './App.tsx'

// Proves the scaffold's client routing/build/test pipeline works end to end, not any real
// customer content - see App.tsx/HomePage.tsx.
describe('App', () => {
  it('renders the placeholder home route', () => {
    render(
      <BrowserRouter>
        <App />
      </BrowserRouter>,
    )

    expect(screen.getByText('Website Development Base')).toBeInTheDocument()
  })
})
