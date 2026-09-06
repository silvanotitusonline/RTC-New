const { test, expect } = require('@appetize/playwright')

const ANDROID_PACKAGE = 'za.org.rtc.community'
const MAIN_ACTIVITY = 'za.org.rtc.community/.MainActivity'
const DEBUG_SESSION_EXTRA = 'za.org.rtc.community.DEBUG_SESSION_ROLE'

async function launchSyntheticResident(session) {
  await session.adbShellCommand(`am force-stop ${ANDROID_PACKAGE}`)
  await session.adbShellCommand(
    `am start -n ${MAIN_ACTIVITY} --es ${DEBUG_SESSION_EXTRA} RESIDENT_A`,
  )
  await expect(session).toHaveElement({
    attributes: { text: 'Welcome back, Resident A' },
  })
}

test.describe('debug synthetic resident navigation', () => {
  test('opens the Account menu and exposes the real sign-out action', async ({ session }) => {
    await launchSyntheticResident(session)

    await session.tap({
      element: { attributes: { 'content-desc': 'Open account or work queue' } },
    })

    await expect(session).toHaveElement({ attributes: { text: 'Account' } })
    await expect(session).toHaveElement({ attributes: { text: 'Sign out' } })
  })

  test('opens every resident primary destination without authentication input or mutation', async ({ session }) => {
    await launchSyntheticResident(session)

    for (const destination of ['Community', 'Explore', 'Support']) {
      await session.tap({ element: { attributes: { text: destination } } })
      await expect(session).toHaveElement({ attributes: { text: destination } })
    }

    await session.tap({ element: { attributes: { text: 'Home' } } })
    await expect(session).toHaveElement({ attributes: { text: 'Welcome back, Resident A' } })
  })
})
