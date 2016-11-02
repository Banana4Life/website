package init

import com.google.inject.AbstractModule

class MainModule extends AbstractModule {
    override def configure(): Unit = {
        bind(classOf[Setup]).asEagerSingleton()
    }
}
