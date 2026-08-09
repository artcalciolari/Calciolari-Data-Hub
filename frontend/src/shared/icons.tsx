export type IconName = 'chart' | 'receipt' | 'box' | 'upload' | 'chevron-left' | 'warning' | 'check' | 'copy'

const PATHS: Record<IconName, string> = {
  chart: 'M3 13h4v8H3zM10 3h4v18h-4zM17 8h4v13h-4z',
  receipt: 'M6 2h12v20l-3-2-3 2-3-2-3 2V2zm3 5h6v2H9zm0 4h6v2H9zm0 4h4v2H9z',
  box: 'M12 2 2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5',
  upload: 'M12 3l5 5h-3v6h-4V8H7l5-5zM4 19h16v2H4z',
  'chevron-left': 'M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z',
  warning: 'M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z',
  check: 'M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
  copy: 'M16 1H4a2 2 0 0 0-2 2v14h2V3h12V1zm3 4H8a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2zm0 16H8V7h11v14z',
}

export function Icon({ name, size = 22 }: { name: IconName; size?: number }) {
  return (
    <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="currentColor">
      <path d={PATHS[name]} />
    </svg>
  )
}
