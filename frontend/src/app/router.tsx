import { createBrowserRouter } from 'react-router-dom'
import { AppLayout } from '@/app/AppLayout'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { ImportsPage } from '@/features/imports/ImportsPage'
import { ImportJobPage } from '@/features/imports/ImportJobPage'
import { ImportFilePage } from '@/features/imports/ImportFilePage'
import { ProductsPage } from '@/features/products/ProductsPage'
import { ProductDetailPage } from '@/features/products/ProductDetailPage'
import { SalesPage } from '@/features/sales/SalesPage'
import { SaleDetailPage } from '@/features/sales/SaleDetailPage'
import { NotFoundPage } from '@/app/NotFoundPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    errorElement: <NotFoundPage />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'imports', element: <ImportsPage /> },
      { path: 'imports/:jobId', element: <ImportJobPage /> },
      { path: 'imports/:jobId/files/:fileId', element: <ImportFilePage /> },
      { path: 'products', element: <ProductsPage /> },
      { path: 'products/:id', element: <ProductDetailPage /> },
      { path: 'sales', element: <SalesPage /> },
      { path: 'sales/:id', element: <SaleDetailPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
