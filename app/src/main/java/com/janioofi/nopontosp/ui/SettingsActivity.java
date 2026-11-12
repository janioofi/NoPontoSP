package com.janioofi.nopontosp.ui;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.janioofi.nopontosp.R;
import com.janioofi.nopontosp.ThemeModeManager;
import com.janioofi.nopontosp.databinding.ActivitySettingsBinding;
import com.janioofi.nopontosp.repository.OlhoVivoRepository;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private OlhoVivoRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = OlhoVivoRepository.getInstance(this);
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        configurarTema();
        binding.btnToggleTokenHelp.setOnClickListener(v -> {
            boolean show = binding.tokenHelpContent.getVisibility() != View.VISIBLE;
            binding.tokenHelpContent.setVisibility(show ? View.VISIBLE : View.GONE);
            binding.btnToggleTokenHelp.setText(show
                    ? R.string.settings_api_help_hide : R.string.settings_api_help_title);
        });
        atualizarStatusToken();
        binding.btnOpenDeveloperPortal.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.sptrans.com.br/desenvolvedores/"))));
        binding.btnSalvarToken.setOnClickListener(v -> salvarToken());
        binding.btnRemoverToken.setOnClickListener(v -> removerToken());
    }

    private void configurarTema() {
        String mode = ThemeModeManager.getMode(this);
        if (ThemeModeManager.MODE_LIGHT.equals(mode)) {
            binding.radioThemeLight.setChecked(true);
        } else if (ThemeModeManager.MODE_DARK.equals(mode)) {
            binding.radioThemeDark.setChecked(true);
        } else {
            binding.radioThemeSystem.setChecked(true);
        }
        binding.radioThemeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String selectedMode = checkedId == R.id.radioThemeLight
                    ? ThemeModeManager.MODE_LIGHT
                    : checkedId == R.id.radioThemeDark
                    ? ThemeModeManager.MODE_DARK
                    : ThemeModeManager.MODE_SYSTEM;
            if (!selectedMode.equals(ThemeModeManager.getMode(this))) {
                ThemeModeManager.setMode(this, selectedMode);
            }
        });
    }

    private void salvarToken() {
        String token = binding.editApiToken.getText() == null
                ? "" : binding.editApiToken.getText().toString().trim();
        if (token.isEmpty()) {
            binding.inputLayoutToken.setError(getString(R.string.error_token_empty));
            return;
        }
        binding.inputLayoutToken.setError(null);
        try {
            repository.saveApiToken(token);
            binding.editApiToken.setText(null);
            atualizarStatusToken();
            binding.textTokenStatus.setText(R.string.msg_token_saved);
        } catch (GeneralSecurityException | IOException error) {
            binding.textTokenStatus.setText(getString(R.string.msg_token_save_error, error.getLocalizedMessage()));
        }
    }

    private void removerToken() {
        try {
            repository.saveApiToken("");
            binding.editApiToken.setText(null);
            binding.textTokenStatus.setText(R.string.msg_token_removed);
        } catch (GeneralSecurityException | IOException error) {
            binding.textTokenStatus.setText(getString(R.string.msg_token_save_error, error.getLocalizedMessage()));
        }
    }

    private void atualizarStatusToken() {
        binding.textTokenStatus.setText(repository.hasConfiguredApiToken()
                ? R.string.msg_token_configured : R.string.msg_token_not_configured);
    }
}
