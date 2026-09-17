import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { LandingPage } from './pages/LandingPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { DashboardPage } from './pages/DashboardPage';
import { DocumentsListPage } from './pages/DocumentsListPage';
import { UploadPage } from './pages/UploadPage';
import { DocumentDetailPage } from './pages/DocumentDetailPage';
import { DocumentReviewPage } from './pages/DocumentReviewPage';
import { RemindersPage } from './pages/RemindersPage';
import { AdminRoute } from './auth/AdminRoute';
import { AdminOverviewPage } from './pages/admin/AdminOverviewPage';
import { AdminUsersPage } from './pages/admin/AdminUsersPage';
import { AdminDocumentsPage } from './pages/admin/AdminDocumentsPage';
import { AdminUploadRulesPage } from './pages/admin/AdminUploadRulesPage';
import { AdminDocumentTypesPage } from './pages/admin/AdminDocumentTypesPage';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <DashboardPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/documents"
        element={
          <ProtectedRoute>
            <DocumentsListPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/documents/upload"
        element={
          <ProtectedRoute>
            <UploadPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/documents/:id"
        element={
          <ProtectedRoute>
            <DocumentDetailPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/documents/:id/review"
        element={
          <ProtectedRoute>
            <DocumentReviewPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/reminders"
        element={
          <ProtectedRoute>
            <RemindersPage />
          </ProtectedRoute>
        }
      />
      <Route path="/admin" element={<AdminRoute><AdminOverviewPage /></AdminRoute>} />
      <Route path="/admin/users" element={<AdminRoute><AdminUsersPage /></AdminRoute>} />
      <Route path="/admin/documents" element={<AdminRoute><AdminDocumentsPage /></AdminRoute>} />
      <Route path="/admin/upload-rules" element={<AdminRoute><AdminUploadRulesPage /></AdminRoute>} />
      <Route path="/admin/document-types" element={<AdminRoute><AdminDocumentTypesPage /></AdminRoute>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
