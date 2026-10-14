package app.messages.data.telephony

import app.messages.data.telephony.model.Conversation
import java.util.Date

/**
 * Sample conversations used by Compose `@Preview` composables and the
 * `FakeConversationRepository`. Not bound at runtime — the real device
 * always uses the Telephony-backed repo.
 */
object FakeConversations {

    fun list(now: Date = Date()): List<Conversation> {
        val n = now.time
        fun minus(minutes: Long = 0, hours: Long = 0, days: Long = 0): Date {
            val ms = minutes * 60_000 + hours * 3_600_000 + days * 86_400_000
            return Date(n - ms)
        }

        return listOf(
            Conversation(
                threadId = "t_pin_mom",
                addresses = listOf("+14155551001"),
                contactName = "Mom",
                snippet = "Don't forget Dad's birthday on Saturday 🎂",
                lastActivity = minus(minutes = 6),
                unreadCount = 2,
                isPinned = true,
            ),
            Conversation(
                threadId = "t_pin_work",
                addresses = listOf("+14155551002"),
                contactName = "Work — On-call",
                snippet = "Pager: db-replica-2 lag > 30s",
                lastActivity = minus(minutes = 18),
                unreadCount = 0,
                isPinned = true,
            ),
            Conversation(
                threadId = "t_alex",
                addresses = listOf("+14155551003"),
                contactName = "Alex Chen",
                snippet = "See you at 7 — table booked under my name",
                lastActivity = minus(minutes = 42),
                unreadCount = 1,
            ),
            Conversation(
                threadId = "t_bank",
                addresses = listOf("VM-HDFCBK"),
                contactName = "HDFC Bank",
                snippet = "INR 1,249.00 debited from a/c **3421 on 12-Jun-26. Avl bal: 47,118.20",
                lastActivity = minus(hours = 2, minutes = 11),
                unreadCount = 1,
            ),
            Conversation(
                threadId = "t_group",
                addresses = listOf("+14155551004", "+14155551005", "+14155551006"),
                contactName = "Trip planning",
                snippet = "Riya: I can drive on Saturday morning",
                lastActivity = minus(hours = 3, minutes = 5),
                unreadCount = 4,
                hasAttachment = true,
            ),
            Conversation(
                threadId = "t_otp",
                addresses = listOf("AD-AMAZON"),
                contactName = "Amazon",
                snippet = "Your OTP is 482910. Do not share with anyone.",
                lastActivity = minus(hours = 5),
                unreadCount = 0,
            ),
            Conversation(
                threadId = "t_priya",
                addresses = listOf("+14155551007"),
                contactName = "Priya",
                snippet = "thanks! 🙌",
                lastActivity = minus(hours = 8),
                unreadCount = 0,
            ),
            Conversation(
                threadId = "t_dentist",
                addresses = listOf("+14155551008"),
                contactName = "Dr. Mehra's clinic",
                snippet = "Reminder: appointment tomorrow at 11:30 AM",
                lastActivity = minus(hours = 13),
                unreadCount = 0,
            ),
            Conversation(
                threadId = "t_landlord",
                addresses = listOf("+14155551009"),
                contactName = null,
                snippet = "Hi this is the plumber, can come by at 4?",
                lastActivity = minus(hours = 22),
                unreadCount = 0,
            ),
            Conversation(
                threadId = "t_jay",
                addresses = listOf("+14155551010"),
                contactName = "Jay",
                snippet = "You: sounds good, see you then",
                lastActivity = minus(days = 1, hours = 2),
                unreadCount = 0,
                hasDraft = true,
            ),
            Conversation(
                threadId = "t_mehul",
                addresses = listOf("+14155551011"),
                contactName = "Mehul",
                snippet = "Photo",
                lastActivity = minus(days = 2),
                unreadCount = 0,
                hasAttachment = true,
            ),
            Conversation(
                threadId = "t_swiggy",
                addresses = listOf("VK-SWIGGY"),
                contactName = "Swiggy",
                snippet = "Your order is on the way! Track it in the app.",
                lastActivity = minus(days = 3, hours = 4),
                unreadCount = 0,
            ),
            Conversation(
                threadId = "t_uber",
                addresses = listOf("VM-UBER"),
                contactName = "Uber",
                snippet = "Trip receipt for Tue, Jun 9 — INR 218.00",
                lastActivity = minus(days = 4),
                unreadCount = 0,
            ),
            Conversation(
                threadId = "t_school",
                addresses = listOf("VK-SCHOOL"),
                contactName = "Greenwood School",
                snippet = "PTA meeting Friday 5:30 PM in the auditorium.",
                lastActivity = minus(days = 6),
                unreadCount = 0,
            ),
            Conversation(
                threadId = "t_old_friend",
                addresses = listOf("+14155551012"),
                contactName = "Karan",
                snippet = "Happy New Year man! 🎉",
                lastActivity = minus(days = 14),
                unreadCount = 0,
            ),
        )
    }
}
