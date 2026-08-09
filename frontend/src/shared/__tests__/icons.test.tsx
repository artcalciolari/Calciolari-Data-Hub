import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Icon, type IconName } from '../icons'

const ICON_NAMES: IconName[] = [
  'chart',
  'receipt',
  'box',
  'upload',
  'chevron-left',
  'warning',
  'check',
  'copy',
  'money',
  'list',
  'trophy',
  'clock',
  'inventory',
  'cart',
  'items',
  'ticket',
  'tag',
]

describe('Icon', () => {
  it.each(ICON_NAMES)('renders icon %s', (name) => {
    const { container } = render(<Icon name={name} size={16} strokeWidth={2} />)
    expect(container.querySelector('svg')).toBeInTheDocument()
  })
})
