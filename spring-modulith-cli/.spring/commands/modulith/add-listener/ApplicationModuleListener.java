package {{root-package}}.{{module}};

import org.springframework.stereotype.Component;

@Component
class {{capitalize name}}EventListener {

	@ApplicationModuleListener
	void on({{eventType}} event) {

	}
}
