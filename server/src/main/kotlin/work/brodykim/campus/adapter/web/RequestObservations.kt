package work.brodykim.campus.adapter.web

import io.micrometer.common.KeyValues
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention
import org.springframework.http.server.observation.ServerRequestObservationContext
import org.springframework.stereotype.Component

@Component
class RequestObservations : DefaultServerRequestObservationConvention() {
    // Route templates remain available; raw URLs can contain personal data and evidence.
    override fun getHighCardinalityKeyValues(context: ServerRequestObservationContext): KeyValues = KeyValues.empty()
}
