package init

import java.util.Locale
import javax.inject.Singleton

@Singleton
class Setup {
    Locale.setDefault(Locale.US)
}
