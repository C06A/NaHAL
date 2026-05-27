package com.helpchoice.nahal.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helpchoice.nahal.ui.NaHalMonoFont
import com.helpchoice.nahal.ui.NaHalSansFont
import com.helpchoice.nahal.ui.LocalNaHalColors

data class AccordionSection(
    val key: String,
    val title: String,
    val count: Int? = null,
    val subtitle: String? = null,
    val content: @Composable () -> Unit,
)

@Composable
fun Accordion(
    sections: List<AccordionSection>,
    openSections: Set<String>,
    onToggle: (String) -> Unit,
) {
    val c = LocalNaHalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sections.forEach { section ->
            val isOpen = section.key in openSections
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(c.bg2)
                    .border(1.dp, if (isOpen) c.border2 else c.border, RoundedCornerShape(5.dp)),
            ) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.bg3)
                        .clickable { onToggle(section.key) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (isOpen) "▾" else "▸",
                        color = c.text3,
                        fontSize = 9.sp,
                        modifier = Modifier.width(10.dp),
                    )
                    Text(
                        text = section.title,
                        color = c.text,
                        fontSize = 11.sp,
                        fontFamily = NaHalMonoFont,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp,
                    )
                    if (section.count != null) {
                        CountBadge(section.count)
                    }
                    if (section.subtitle != null) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = section.subtitle,
                            color = c.text3,
                            fontSize = 10.sp,
                            fontFamily = NaHalSansFont,
                        )
                    }
                }

                // Body
                if (isOpen) {
                    NaHalDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        section.content()
                    }
                }
            }
        }
    }
}
