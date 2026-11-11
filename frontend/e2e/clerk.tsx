export const getToken = async () => 'test-only-token'
export const useAuth = () => ({ isLoaded: true, isSignedIn: true })
export const useClerk = () => ({ status: 'ready', openUserProfile() {}, signOut() {} })
export const useUser = () => ({ user: { firstName: 'Test', fullName: 'Test User', primaryEmailAddress: { emailAddress: 'test@example.invalid' } } })
export const SignIn = () => <p>Test sign in</p>
