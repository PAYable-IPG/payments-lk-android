package lk.payments.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheckoutResultTest {

    private val scheme = "myshop"
    private val base = "myshop://payments-lk/return"

    private fun parse(url: String, expectedScheme: String = scheme) = CheckoutResult.fromUrl(url, expectedScheme)

    @Test
    fun parsesSucceeded() {
        assertEquals(
            CheckoutResult(CheckoutStatus.SUCCEEDED, "chk_abc123"),
            parse("$base?checkout=chk_abc123&status=succeeded"),
        )
    }

    @Test
    fun parsesEveryRedirectStatus() {
        assertEquals(CheckoutStatus.FAILED, parse("$base?checkout=chk_a1&status=failed")?.status)
        assertEquals(CheckoutStatus.CANCELED, parse("$base?checkout=chk_a1&status=canceled")?.status)
        assertEquals(CheckoutStatus.EXPIRED, parse("$base?checkout=chk_a1&status=expired")?.status)
    }

    @Test
    fun parameterOrderDoesNotMatter() {
        assertEquals(
            CheckoutResult(CheckoutStatus.CANCELED, "chk_x9"),
            parse("$base?status=canceled&checkout=chk_x9"),
        )
    }

    @Test
    fun keepsWorkingWithExistingQueryParameters() {
        assertEquals(
            CheckoutResult(CheckoutStatus.SUCCEEDED, "chk_q1"),
            parse("$base?order=1042&lang=si&checkout=chk_q1&status=succeeded"),
        )
    }

    @Test
    fun ignoresFragment() {
        assertEquals(
            CheckoutResult(CheckoutStatus.SUCCEEDED, "chk_f1"),
            parse("$base?checkout=chk_f1&status=succeeded#done"),
        )
    }

    @Test
    fun schemeComparisonIgnoresCase() {
        assertEquals(CheckoutStatus.SUCCEEDED, parse("MyShop://payments-lk/return?checkout=chk_c1&status=succeeded")?.status)
        assertEquals(CheckoutStatus.SUCCEEDED, parse("$base?checkout=chk_c1&status=succeeded", "MYSHOP")?.status)
    }

    @Test
    fun rejectsWrongScheme() {
        assertNull(parse("othershop://payments-lk/return?checkout=chk_a1&status=succeeded"))
        assertNull(parse("https://example.com/return?checkout=chk_a1&status=succeeded"))
        assertNull(parse("myshopx://payments-lk/return?checkout=chk_a1&status=succeeded"))
    }

    @Test
    fun rejectsBlankExpectedScheme() {
        assertNull(parse("$base?checkout=chk_a1&status=succeeded", ""))
        assertNull(parse("$base?checkout=chk_a1&status=succeeded", " "))
    }

    @Test
    fun rejectsMissingParameters() {
        assertNull(parse("$base?status=succeeded"))
        assertNull(parse("$base?checkout=chk_a1"))
        assertNull(parse(base))
        assertNull(parse("$base?"))
    }

    @Test
    fun rejectsParametersWithoutValues() {
        assertNull(parse("$base?checkout=chk_a1&status"))
        assertNull(parse("$base?checkout&status=succeeded"))
        assertNull(parse("$base?checkout=chk_a1&status="))
    }

    @Test
    fun rejectsDuplicates() {
        assertNull(parse("$base?checkout=chk_a1&checkout=chk_b2&status=succeeded"))
        assertNull(parse("$base?checkout=chk_a1&checkout=chk_a1&status=succeeded"))
        assertNull(parse("$base?checkout=chk_a1&status=failed&status=succeeded"))
        assertNull(parse("$base?checkout=chk_a1&status=succeeded&status=succeeded"))
    }

    @Test
    fun rejectsDuplicatesHiddenByPercentEncodedNames() {
        assertNull(parse("$base?checkout=chk_a1&chec%6Bout=chk_b2&status=succeeded"))
    }

    @Test
    fun rejectsInvalidCheckoutIds() {
        assertNull(parse("$base?checkout=chk_&status=succeeded"))
        assertNull(parse("$base?checkout=chk_ABC&status=succeeded"))
        assertNull(parse("$base?checkout=abc123&status=succeeded"))
        assertNull(parse("$base?checkout=CHK_abc&status=succeeded"))
        assertNull(parse("$base?checkout=chk_ab-c&status=succeeded"))
        assertNull(parse("$base?checkout=chk_ab%20c&status=succeeded"))
        assertNull(parse("$base?checkout=chk_ab+c&status=succeeded"))
        assertNull(parse("$base?checkout=xchk_abc&status=succeeded"))
        assertNull(parse("$base?checkout=chk_abc%0A&status=succeeded"))
        assertNull(parse("$base?checkout=chk_%41&status=succeeded"))
    }

    @Test
    fun acceptsIdsUpTo64CharactersAfterPrefix() {
        val longest = "chk_" + "a".repeat(64)
        assertEquals(longest, parse("$base?checkout=$longest&status=succeeded")?.checkoutId)
        assertNull(parse("$base?checkout=chk_${"a".repeat(65)}&status=succeeded"))
    }

    @Test
    fun decodesPercentEncodedValues() {
        assertEquals(
            CheckoutResult(CheckoutStatus.SUCCEEDED, "chk_abc"),
            parse("$base?checkout=chk_%61bc&status=succ%65eded"),
        )
    }

    @Test
    fun rejectsUnknownStatuses() {
        assertNull(parse("$base?checkout=chk_a1&status=SUCCEEDED"))
        assertNull(parse("$base?checkout=chk_a1&status=paid"))
        assertNull(parse("$base?checkout=chk_a1&status=dismissed"))
        assertNull(parse("$base?checkout=chk_a1&status=cancelled"))
        assertNull(parse("$base?checkout=chk_a1&status=succeeded%20"))
    }

    @Test
    fun rejectsMalformedAndOpaqueUrls() {
        assertNull(parse(""))
        assertNull(parse("not a url"))
        assertNull(parse("myshop://payments-lk/return?checkout=chk_a1&status=succeeded&x=100%"))
        assertNull(parse("myshop:return?checkout=chk_a1&status=succeeded"))
        assertNull(parse("//payments-lk/return?checkout=chk_a1&status=succeeded"))
    }

    @Test
    fun dismissedConstantHasNoCheckoutId() {
        assertEquals(CheckoutStatus.DISMISSED, CheckoutResult.DISMISSED.status)
        assertNull(CheckoutResult.DISMISSED.checkoutId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructorRejectsRedirectStatusWithoutId() {
        CheckoutResult(CheckoutStatus.SUCCEEDED, null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructorRejectsDismissedWithId() {
        CheckoutResult(CheckoutStatus.DISMISSED, "chk_a1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructorRejectsMalformedId() {
        CheckoutResult(CheckoutStatus.FAILED, "not_an_id")
    }
}
