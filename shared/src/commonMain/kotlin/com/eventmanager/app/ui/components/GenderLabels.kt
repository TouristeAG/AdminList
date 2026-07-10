package com.eventmanager.app.ui.components

import androidx.compose.runtime.Composable
import com.eventmanager.app.data.models.Gender
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun genderDisplayLabel(gender: Gender): String = when (gender) {
    Gender.FEMALE -> stringResource(Res.string.gender_female)
    Gender.MALE -> stringResource(Res.string.gender_male)
    Gender.NON_BINARY -> stringResource(Res.string.gender_non_binary)
    Gender.OTHER -> stringResource(Res.string.gender_other)
    Gender.PREFER_NOT_TO_DISCLOSE -> stringResource(Res.string.gender_prefer_not_to_disclose)
}
