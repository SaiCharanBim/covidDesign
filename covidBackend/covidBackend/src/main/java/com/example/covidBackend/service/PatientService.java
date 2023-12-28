package com.example.covidBackend.service;

import com.example.covidBackend.dto.request.PatientLoginDTO;
import com.example.covidBackend.dto.request.PatientSignupDTO;
import com.example.covidBackend.dto.response.AppointmentDTO;
import com.example.covidBackend.exceptions.PatientDoesNotExistException;
import com.example.covidBackend.exceptions.WrongCredentials;
import com.example.covidBackend.models.Doctor;
import com.example.covidBackend.models.Patient;
import com.example.covidBackend.models.VaccinationCenter;
import com.example.covidBackend.repository.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PatientService {

    @Autowired
    PatientRepository patientRepository;

    @Autowired
    VaccinationCenterService vaccinationCenterService;

    @Autowired
    DoctorService doctorService;

    @Autowired
    MailService mailService;

    public Patient signUp(PatientSignupDTO patientSignupDTO){
        Patient patient = new Patient();
        patient.setName(patientSignupDTO.getName());
        patient.setGender(patientSignupDTO.getGender());
        patient.setEmail(patientSignupDTO.getEmail());
        patient.setAddress(patientSignupDTO.getAddress());
        patient.setAadharNumber(patientSignupDTO.getAadharNumber());
        patient.setPassword(patientSignupDTO.getPassword());
        patient.setPhoneNumber(patientSignupDTO.getPhoneNumber());
        patient.setVaccinationPrefrence(patientSignupDTO.getVaccinationPrefrence().toString());
        patientRepository.save(patient);
        return patient;
    }


    public Patient login(PatientLoginDTO patientLoginDTO){
        Patient patient = patientRepository.getPatientByEmail(patientLoginDTO.getEmail());
        if(patient == null){
            throw new PatientDoesNotExistException("Patient email Id is not registered in our portal.");
        }
        if(!patient.getPassword().equals(patientLoginDTO.getPassword())){
            throw new WrongCredentials("Patient Entered Wrong Password.");
        }
        return patient;
    }

    public AppointmentDTO createAppointment(String email, String vaccinationCenterPrefrence){
        // 1. get patient by email
        Patient p = patientRepository.getPatientByEmail(email);
        // 2. Identify patient vaccination prefrence
        String vPrefrence = p.getVaccinationPrefrence();
        List<VaccinationCenter> vcList = vaccinationCenterService.getMinimumVCOnTheBasisOfTypeAndPrefrence(vaccinationCenterPrefrence, vPrefrence);
        // 3. Assigning 0th index vaccination center to patient
        VaccinationCenter patientsVC = vcList.get(0);
        // 4. Assign doctor who is handeling minimum number of patients to the current patient
        List<Doctor> docList = doctorService.getMinimumDoctorOnTheBasisOfVC(patientsVC.getId());
        // 5. Take out minimum doctor
        Doctor patientDoctor = docList.get(0);

        updateDoseCountByOne(p);
        vaccinationCenterService.updatePatientCountByOne(patientsVC);
        doctorService.updatePatientCountByOne(patientDoctor);
        patientDoctor.getPatients().add(p);
        doctorService.addPatientVsDoctor(p.getId(), patientDoctor.getId());
        AppointmentDTO appointmentDTO = new AppointmentDTO();
        appointmentDTO.setPatient(p);
        appointmentDTO.setDoseNumber(p.getDoseCount() + 1);
        appointmentDTO.setDocID(patientDoctor.getId());
        appointmentDTO.setDocName(patientDoctor.getName());
        appointmentDTO.setVcID(patientsVC.getId());
        appointmentDTO.setVaccinationCenterName(patientsVC.getName());

        String to = p.getEmail();
        String sub = String.format("Congratulations !! %s your appointment got created", p.getName());
        String text = String.format("Hii %s," +
                        "\n Your appointment got created. Below are your appointment details :" +
                        "\n1. Dose Count : %d" +
                        "\n2. Doctor Name : %s" +
                        "\n3. Vaccination Center Name  : %s" +
                        "\n4. Vaccination Center Address : %s",
                p.getName(),
                p.getDoseCount(),
                patientDoctor.getName(),
                patientsVC.getName(),
                patientsVC.getAddress()
        );
        mailService.generateMail(to,sub,text);
        return appointmentDTO;
    }

    public void updateDoseCountByOne(Patient patient){
        UUID id = patient.getId();
        int doseCount = patient.getDoseCount() + 1;
        patientRepository.updateDoseCountByOne(id, doseCount);
    }
}
