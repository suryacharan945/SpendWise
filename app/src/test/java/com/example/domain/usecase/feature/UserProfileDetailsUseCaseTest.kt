package com.example.domain.usecase.feature

import com.example.core.common.Resource
import com.example.data.model.Avatar
import com.example.data.model.BudgetStyle
import com.example.data.model.RiskAppetite
import com.example.data.model.UserProfile
import com.example.data.model.UserProfileDetails
import com.example.domain.model.Money
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserProfileDetailsUseCaseTest {

    private val useCase = UserProfileDetailsUseCase()

    @Test
    fun `getDefaultProfileDetails initializes with proportional numbers from UserProfile`() {
        val profile = UserProfile(
            email = "surya@smartbudget.com",
            username = "Surya",
            monthlyIncome = 60000.0,
            monthlySavingsGoal = 12000.0
        )

        val details = useCase.getDefaultProfileDetails(profile)
        assertEquals("Surya", details.displayName)
        assertEquals(Money.fromRupees(60000.0), details.monthlyIncome)
        assertEquals(20, details.savingsTargetPercent)
        assertEquals(RiskAppetite.BALANCED, details.riskAppetite)
        assertEquals(BudgetStyle.ZERO_BASED, details.budgetStyle)
    }

    @Test
    fun `computeDailyAllowance divides remaining discretionary budget in exact integer paise`() {
        // Income = ₹60,000, Commitments = ₹18,000 (30%), Savings Target = 20% (₹12,000)
        // Discretionary budget = ₹60,000 - ₹18,000 - ₹12,000 = ₹30,000 (3,000,000 paise)
        // 30 days remaining -> ₹1,000 per day (100,000 paise)
        val details = UserProfileDetails(
            displayName = "Surya",
            avatar = Avatar.Emoji("🦁"),
            monthlyIncome = Money.fromRupees(60000.0),
            salaryDayOfMonth = 1,
            fixedMonthlyCommitments = Money.fromRupees(18000.0),
            savingsTargetPercent = 20
        )

        val allowance = useCase.calculateDailyAllowance(details, remainingDaysInMonth = 30)
        assertEquals(Money.fromRupees(1000.0), allowance)
        assertEquals("₹1,000.00", allowance.format())
    }

    @Test
    fun `computeDailyAllowance clamps to zero if commitments and savings exceed income`() {
        val details = UserProfileDetails(
            displayName = "Surya",
            avatar = Avatar.Emoji("🦁"),
            monthlyIncome = Money.fromRupees(30000.0),
            salaryDayOfMonth = 1,
            fixedMonthlyCommitments = Money.fromRupees(25000.0),
            savingsTargetPercent = 30 // ₹9,000 -> Total required = ₹34,000 > ₹30,000
        )

        val allowance = useCase.calculateDailyAllowance(details, remainingDaysInMonth = 25)
        assertEquals(Money.ZERO, allowance)
        assertTrue(allowance.isZero)
    }

    @Test
    fun `validateProfile rejects invalid names, salary dates, or exceeding commitments`() {
        val valid = UserProfileDetails(
            displayName = "Valid User",
            avatar = Avatar.Initial("V"),
            monthlyIncome = Money.fromRupees(50000.0),
            salaryDayOfMonth = 5,
            fixedMonthlyCommitments = Money.fromRupees(15000.0),
            savingsTargetPercent = 20
        )
        assertTrue(useCase.validateProfile(valid) is Resource.Success)

        val emptyName = valid.copy(displayName = "")
        assertTrue(useCase.validateProfile(emptyName) is Resource.Error)

        val invalidSalaryDay = valid.copy(salaryDayOfMonth = 35)
        assertTrue(useCase.validateProfile(invalidSalaryDay) is Resource.Error)

        val excessiveCommitments = valid.copy(fixedMonthlyCommitments = Money.fromRupees(70000.0))
        assertTrue(useCase.validateProfile(excessiveCommitments) is Resource.Error)
    }
}
