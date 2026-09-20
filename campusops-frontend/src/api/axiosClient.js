import axios from 'axios'

const axiosClient = axios.create({
  baseURL: 'http://localhost:8080/api',
  headers: { 'Content-Type': 'application/json' },
})

axiosClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const requestUrl = error.config?.url ?? ''
    // Un 401 sur la tentative de connexion elle-même doit être géré par la page
    // de connexion (message d'erreur), pas déclencher une redirection/rechargement
    // qui effacerait le message affiché à l'utilisateur.
    const isLoginRequest = requestUrl.includes('/auth/login')
    const alreadyOnLogin =
      typeof window !== 'undefined' && window.location.pathname === '/login'

    if (status === 401 && !isLoginRequest && !alreadyOnLogin) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('user')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default axiosClient