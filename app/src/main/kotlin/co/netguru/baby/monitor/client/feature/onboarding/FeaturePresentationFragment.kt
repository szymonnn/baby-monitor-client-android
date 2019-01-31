package co.netguru.baby.monitor.client.feature.onboarding

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import co.netguru.baby.monitor.client.R
import kotlinx.android.synthetic.main.onboarding_buttons.*

class FeaturePresentationFragment : Fragment() {

    private var layoutResource = R.layout.fragment_feature_a

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        layoutResource = when (arguments?.getString(FEATURE_KEY)) {
            FEATURE_B -> R.layout.fragment_feature_b
            FEATURE_C -> R.layout.fragment_feature_c
            else -> R.layout.fragment_feature_a
        }
        return inflater.inflate(layoutResource, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        featureNextBtn.setOnClickListener {
            handleNextClicked()
        }
        featureSkipBtn.setOnClickListener {
            findNavController().navigate(R.id.featureToInfoAboutDevices)
        }
    }

    private fun handleNextClicked() {
        val nextFeature = when (layoutResource) {
            R.layout.fragment_feature_a -> FEATURE_B
            R.layout.fragment_feature_b -> FEATURE_C
            else -> ""
        }
        val bundle = Bundle().apply {
            putString(FEATURE_KEY, nextFeature)
        }
        findNavController().navigate(
                if (nextFeature.isEmpty()) {
                    R.id.featureToInfoAboutDevices
                } else {
                    R.id.featureToFeature
                },
                bundle
        )
    }

    companion object {
        private const val FEATURE_KEY = "FEATURE_KEY"
        private const val FEATURE_B = "FEATURE_B"
        private const val FEATURE_C = "FEATURE_C"
    }
}