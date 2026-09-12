import { Route, Routes } from 'react-router-dom'
import { HomePage } from './pages/HomePage.tsx'

// Design-neutral Development Base: exactly one placeholder route, proving client routing works
// without prescribing any customer-visible page/navigation structure. A Developer execution adds
// real routes here for the target Design Proposal's actual pages - this file is not itself
// customer content.
export function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
    </Routes>
  )
}

export default App
