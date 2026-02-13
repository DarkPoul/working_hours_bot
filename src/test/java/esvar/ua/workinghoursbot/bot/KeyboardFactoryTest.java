package esvar.ua.workinghoursbot.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class KeyboardFactoryTest {

    @Test
    void roleKeyboardContainsOnlySellerAndTmRoles() {
        var markup = KeyboardFactory.roleMenuKeyboard();

        List<String> labels = markup.getKeyboard().stream()
                .flatMap(row -> row.stream().map(button -> button.getText()))
                .collect(Collectors.toList());

        assertEquals(List.of(
                "Продавець",
                "ТМ",
                "⬅️ Назад",
                "🔁 Почати спочатку"
        ), labels);
    }

    @Test
    void tmMainMenuDoesNotContainMyLocationButton() {
        var markup = KeyboardFactory.tmMainMenuKeyboard();

        List<String> labels = markup.getKeyboard().stream()
                .flatMap(row -> row.stream().map(button -> button.getText()))
                .collect(Collectors.toList());

        assertEquals(List.of(
                "Заявки",
                "Локації",
                "Графік локацій"
        ), labels);
    }

}
