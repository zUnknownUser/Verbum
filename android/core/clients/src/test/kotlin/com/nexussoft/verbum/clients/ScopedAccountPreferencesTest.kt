package com.nexussoft.verbum.clients
import kotlin.test.*
class ScopedAccountPreferencesTest {
 @Test fun registrationTransfersGuestNotesAndRotatesTheGuestNamespace() {
  val global=InMemoryPreferencesClient();val guest=InMemoryPreferencesClient(mapOf("readerAnnotations" to "guest notes"));val account=InMemoryPreferencesClient()
  val oldScope=ScopedAccountPreferences.scope(null)
  val old=ScopedAccountPreferences(global,guest,oldScope)
  ScopedAccountPreferences.promote(global,guest,account,oldScope)
  assertEquals("guest notes",account.string("readerAnnotations"))
  assertNull(old.string("readerAnnotations"))
  assertFailsWith<IllegalStateException>{old.setString("readerAnnotations","late")}
  assertNotEquals(oldScope,ScopedAccountPreferences.scope(null,global.string("guestGeneration")!!))
 }

 @Test fun notesRemainSeparateAndOldClientCannotWriteIntoNewAccount() {
  val global=InMemoryPreferencesClient();val a=InMemoryPreferencesClient();val b=InMemoryPreferencesClient()
  val first=ScopedAccountPreferences(global,a,"a");val second=ScopedAccountPreferences(global,b,"b")
  first.setString("readerAnnotations","private A");assertNull(second.string("readerAnnotations"))
  second.setString("readerAnnotations","private B");first.setString("readerAnnotations","late A")
  assertEquals("private B",second.string("readerAnnotations"));assertEquals("late A",first.string("readerAnnotations"))
  ScopedAccountPreferences.delete(global,a,"a")
  assertNull(first.string("readerAnnotations"));assertFailsWith<IllegalStateException>{first.setString("readerAnnotations","late save")}
  assertEquals("private B",second.string("readerAnnotations"))
 }
 @Test fun migrationRunsOnlyForInitialIdentityAndPreservesGuestData() {
  val global=InMemoryPreferencesClient(mapOf("readerAnnotations" to "legacy notes","lastRead" to "John.3"))
  val guest=InMemoryPreferencesClient();val signedIn=InMemoryPreferencesClient()
  ScopedAccountPreferences.prepare(global,guest);ScopedAccountPreferences.prepare(global,signedIn)
  assertEquals("legacy notes",guest.string("readerAnnotations"));assertNull(signedIn.string("readerAnnotations"))
  assertEquals("",global.string("readerAnnotations"));assertEquals("John.3",guest.string("lastRead"))
  assertNotEquals(ScopedAccountPreferences.scope(null),ScopedAccountPreferences.scope("local-guest"))
  assertEquals(64,ScopedAccountPreferences.scope("../../account").length)
 }
}
