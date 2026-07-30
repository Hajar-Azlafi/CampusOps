export function downloadBlob(blobData, filename) {
  const url = URL.createObjectURL(blobData instanceof Blob ? blobData : new Blob([blobData]))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export function downloadCsvFromAccounts(createdAccounts, filename = 'rapport_import.csv') {
  const header = 'Email;Mot de passe temporaire\n'
  const rows = createdAccounts.map((a) => `${a.email};${a.temporaryPassword}`).join('\n')
  const csvContent = '\uFEFF' + header + rows
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  downloadBlob(blob, filename)
}