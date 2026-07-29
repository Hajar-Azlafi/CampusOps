import { createContext, useContext, useState, useEffect } from 'react'
import axiosClient from '../api/axiosClient'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const storedUser = localStorage.getItem('user') || sessionStorage.getItem('user')
    const token = localStorage.getItem('token') || sessionStorage.getItem('token')
    if (storedUser && token) {
      setUser(JSON.parse(storedUser))
    }
    setLoading(false)
  }, [])

  const login = async (email, password, remember = true) => {
    const response = await axiosClient.post('/auth/login', { email, password })
    const { token, ...userData } = response.data

    const storage = remember ? localStorage : sessionStorage
    const other = remember ? sessionStorage : localStorage
    other.removeItem('token')
    other.removeItem('user')
    storage.setItem('token', token)
    storage.setItem('user', JSON.stringify(userData))

    setUser(userData)
    return userData
  }

  const logout = () => {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('user')
    setUser(null)
  }

  const markPasswordChanged = () => {
    const updatedUser = { ...user, mustChangePassword: false }
    setUser(updatedUser)

    const storage = localStorage.getItem('user') ? localStorage : sessionStorage
    storage.setItem('user', JSON.stringify(updatedUser))
  }

  const value = { user, isAuthenticated: !!user, loading, login, logout, markPasswordChanged }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error("useAuth doit etre utilise a l'interieur d'un AuthProvider")
  }
  return context
}