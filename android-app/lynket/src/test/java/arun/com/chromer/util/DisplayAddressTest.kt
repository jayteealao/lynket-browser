package arun.com.chromer.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The address line and the title of History and Tabs rows, over the worst addresses people keep. */
class DisplayAddressTest {

  private val fileUrl = "file:///storage/emulated/0/Download/report-2026-q3-final-v2.pdf"

  @Test
  fun dropsTheWebSchemeAndALeadingWww() {
    assertThat(displayAddress("https://www.example-company.co.uk/dashboard/overview"))
      .isEqualTo("example-company.co.uk/dashboard/overview")
  }

  @Test
  fun dropsTheSchemeAndWwwInAnyCaseAndKeepsQueryAndFragment() {
    assertThat(displayAddress("HTTP://WWW.Example.com/A?b=1#c")).isEqualTo("Example.com/A?b=1#c")
  }

  @Test
  fun aHostOnlyAddressLosesItsLoneSlash() {
    assertThat(displayAddress("https://github.com/")).isEqualTo("github.com")
  }

  @Test
  fun anIpAddressKeepsItsPortPathAndQuery() {
    assertThat(displayAddress("http://192.168.1.1:8080/settings/network/wireless?band=5ghz"))
      .isEqualTo("192.168.1.1:8080/settings/network/wireless?band=5ghz")
  }

  @Test
  fun userInfoIsDroppedLikeTheTitleHost() {
    val url = "https://user:pw@www.example.com/a"
    assertThat(displayAddress(url)).isEqualTo("example.com/a")
    assertThat(displayHost(url)).isEqualTo("example.com")
  }

  @Test
  fun wwwAloneIsAHost() {
    assertThat(displayAddress("http://www/")).isEqualTo("www")
  }

  @Test
  fun otherSchemesStayAsGiven() {
    assertThat(displayAddress(fileUrl)).isEqualTo(fileUrl)
    assertThat(displayAddress("content://downloads/document/12"))
      .isEqualTo("content://downloads/document/12")
  }

  @Test
  fun theHostDropsUserInfoAndWww() {
    assertThat(displayHost("https://user@www.example.com/x")).isEqualTo("example.com")
    assertThat(displayHost(fileUrl)).isNull()
  }

  @Test
  fun anUntitledWebPageIsTitledByItsHost() {
    assertThat(rowTitle(null, "https://github.com/")).isEqualTo("github.com")
  }

  @Test
  fun anUntitledFileIsTitledByItsFileName() {
    assertThat(rowTitle("", fileUrl)).isEqualTo("report-2026-q3-final-v2.pdf")
  }

  @Test
  fun anUntitledAddressWithNoHostOrPathIsTitledByItsAddress() {
    assertThat(rowTitle("  ", "about:blank")).isEqualTo("about:blank")
  }

  @Test
  fun aTitleWins() {
    assertThat(rowTitle("Title", "https://github.com/")).isEqualTo("Title")
    assertThat(rowTitle("Title", fileUrl)).isEqualTo("Title")
  }

  @Test
  fun anEmptyOrBlankAddressGivesEmptyTextAndNeverThrows() {
    assertThat(displayAddress("")).isEmpty()
    assertThat(displayAddress("   ")).isEmpty()
    assertThat(displayHost("")).isNull()
    assertThat(rowTitle(null, "")).isEmpty()
    assertThat(rowTitle(null, "  ")).isEmpty()
    assertThat(displayAddress("https://")).isEmpty()
  }
}
