import type { ComponentType } from 'react'
import {
  BarChart3,
  Receipt,
  Package,
  Upload,
  ChevronLeft,
  ChevronRight,
  AlertTriangle,
  Check,
  Copy,
  DollarSign,
  List,
  Trophy,
  Clock,
  ShoppingCart,
  ClipboardList,
  Ticket,
  Tag,
  type LucideProps,
} from 'lucide-react'

export type IconName =
  | 'chart'
  | 'receipt'
  | 'box'
  | 'upload'
  | 'chevron-left'
  | 'chevron-right'
  | 'warning'
  | 'check'
  | 'copy'
  | 'money'
  | 'list'
  | 'trophy'
  | 'clock'
  | 'inventory'
  | 'cart'
  | 'items'
  | 'ticket'
  | 'tag'

const ICONS: Record<IconName, ComponentType<LucideProps>> = {
  chart: BarChart3,
  receipt: Receipt,
  box: Package,
  upload: Upload,
  'chevron-left': ChevronLeft,
  'chevron-right': ChevronRight,
  warning: AlertTriangle,
  check: Check,
  copy: Copy,
  money: DollarSign,
  list: List,
  trophy: Trophy,
  clock: Clock,
  inventory: Package,
  cart: ShoppingCart,
  items: ClipboardList,
  ticket: Ticket,
  tag: Tag,
}

export function Icon({ name, size = 22, strokeWidth = 1.75 }: { name: IconName; size?: number; strokeWidth?: number }) {
  const Component = ICONS[name]
  return <Component aria-hidden="true" size={size} strokeWidth={strokeWidth} />
}
