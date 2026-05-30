package com.nexmemo.web.seeder;

import com.nexmemo.core.entity.District;
import com.nexmemo.core.entity.MunicipalityVdc;
import com.nexmemo.core.entity.Province;
import com.nexmemo.core.repository.DistrictRepository;
import com.nexmemo.core.repository.MunicipalityVdcRepository;
import com.nexmemo.core.repository.ProvinceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class LocationSeeder implements ApplicationRunner {

    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final MunicipalityVdcRepository municipalityVdcRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (provinceRepository.count() > 0 && districtRepository.count() > 0 && municipalityVdcRepository.count() > 0) {
            log.info("Location data already exists, skipping seed.");
            return;
        }
        seedProvincesIfNeeded();
        seedDistrictsIfNeeded();
        seedMunicipalityVdcIfNeeded();
    }

    @Transactional
    public void seedProvincesIfNeeded() {
        if (provinceRepository.count() > 0) {
            log.info("Provinces already exist, skipping.");
            return;
        }
        log.info("Seeding provinces...");
        List<Province> provinces = Arrays.asList(
            new Province("Koshi", "कोशी प्रदेश", "8465"),
            new Province("Madhesh", "मधेश प्रदेश", "8466"),
            new Province("Bagmati", "बागमती प्रदेश", "8467"),
            new Province("Gandaki", "गण्डकी प्रदेश", "8468"),
            new Province("Lumbini", "लुम्बिनी प्रदेश", "8469"),
            new Province("Karnali", "कर्णाली प्रदेश", "8470"),
            new Province("Sudurpashchim", "सुदूरपश्चिम प्रदेश", "8471")
        );
        provinceRepository.saveAll(provinces);
        log.info("Seeded {} provinces.", provinces.size());
    }

    @Transactional
    public void seedDistrictsIfNeeded() {
        if (districtRepository.count() > 0) {
            log.info("Districts already exist, skipping.");
            return;
        }
        log.info("Seeding districts...");
        Map<String, Province> provinceMap = new HashMap<>();
        for (Province p : provinceRepository.findAll()) {
            provinceMap.put(p.getName(), p);
        }

        List<District> districts = Arrays.asList(
            // Province 1 - Koshi
            new District(provinceMap.get("Koshi"), "Taplejung", "ताप्लेजुङ"),
            new District(provinceMap.get("Koshi"), "Sankhuwasabha", "सङ्खुवासभा"),
            new District(provinceMap.get("Koshi"), "Solukhumbu", "सोलुखुम्बु"),
            new District(provinceMap.get("Koshi"), "Okhaldhunga", "ओखलढुङ्गा"),
            new District(provinceMap.get("Koshi"), "Khotang", "खोटाङ"),
            new District(provinceMap.get("Koshi"), "Bhojpur", "भोजपुर"),
            new District(provinceMap.get("Koshi"), "Dhankuta", "धनकुटा"),
            new District(provinceMap.get("Koshi"), "Terhathum", "तेह्रथुम"),
            new District(provinceMap.get("Koshi"), "Panchthar", "पाँचथर"),
            new District(provinceMap.get("Koshi"), "Ilam", "इलाम"),
            new District(provinceMap.get("Koshi"), "Jhapa", "झापा"),
            new District(provinceMap.get("Koshi"), "Morang", "मोरङ"),
            new District(provinceMap.get("Koshi"), "Sunsari", "सुनसरी"),
            new District(provinceMap.get("Koshi"), "Udayapur", "उदयपुर"),
            // Province 2 - Madhesh
            new District(provinceMap.get("Madhesh"), "Saptari", "सप्तरी"),
            new District(provinceMap.get("Madhesh"), "Siraha", "सिराहा"),
            new District(provinceMap.get("Madhesh"), "Dhanusha", "धनुषा"),
            new District(provinceMap.get("Madhesh"), "Mahottari", "महोत्तरी"),
            new District(provinceMap.get("Madhesh"), "Sarlahi", "सर्लाही"),
            new District(provinceMap.get("Madhesh"), "Rautahat", "रौतहट"),
            new District(provinceMap.get("Madhesh"), "Bara", "बारा"),
            new District(provinceMap.get("Madhesh"), "Parsa", "पर्सा"),
            // Province 3 - Bagmati
            new District(provinceMap.get("Bagmati"), "Sindhuli", "सिन्धुली"),
            new District(provinceMap.get("Bagmati"), "Ramechhap", "रामेछाप"),
            new District(provinceMap.get("Bagmati"), "Dolakha", "दोलखा"),
            new District(provinceMap.get("Bagmati"), "Bhaktapur", "भक्तपुर"),
            new District(provinceMap.get("Bagmati"), "Dhading", "धादिङ"),
            new District(provinceMap.get("Bagmati"), "Kathmandu", "काठमाडौँ"),
            new District(provinceMap.get("Bagmati"), "Kavrepalanchok", "काभ्रेपलान्चोक"),
            new District(provinceMap.get("Bagmati"), "Lalitpur", "ललितपुर"),
            new District(provinceMap.get("Bagmati"), "Nuwakot", "नुवाकोट"),
            new District(provinceMap.get("Bagmati"), "Rasuwa", "रसुवा"),
            new District(provinceMap.get("Bagmati"), "Sindhupalchok", "सिन्धुपाल्चोक"),
            new District(provinceMap.get("Bagmati"), "Chitwan", "चितवन"),
            new District(provinceMap.get("Bagmati"), "Makwanpur", "मकवानपुर"),
            // Province 4 - Gandaki
            new District(provinceMap.get("Gandaki"), "Gorkha", "गोरखा"),
            new District(provinceMap.get("Gandaki"), "Kaski", "कास्की"),
            new District(provinceMap.get("Gandaki"), "Lamjung", "लमजुङ"),
            new District(provinceMap.get("Gandaki"), "Syangja", "स्याङ्जा"),
            new District(provinceMap.get("Gandaki"), "Tanahun", "तनहुँ"),
            new District(provinceMap.get("Gandaki"), "Manang", "मनाङ"),
            new District(provinceMap.get("Gandaki"), "Mustang", "मुस्ताङ"),
            new District(provinceMap.get("Gandaki"), "Myagdi", "म्याग्दी"),
            new District(provinceMap.get("Gandaki"), "Nawalpur", "नवलपुर"),
            new District(provinceMap.get("Gandaki"), "Parbat", "पर्वत"),
            new District(provinceMap.get("Gandaki"), "Baglung", "बागलुङ"),
            // Province 5 - Lumbini
            new District(provinceMap.get("Lumbini"), "Kapilvastu", "कपिलवस्तु"),
            new District(provinceMap.get("Lumbini"), "Parasi", "परासी"),
            new District(provinceMap.get("Lumbini"), "Rupandehi", "रुपन्देही"),
            new District(provinceMap.get("Lumbini"), "Palpa", "पाल्पा"),
            new District(provinceMap.get("Lumbini"), "Gulmi", "गुल्मी"),
            new District(provinceMap.get("Lumbini"), "Arghakhanchi", "अर्घाखाँची"),
            new District(provinceMap.get("Lumbini"), "Pyuthan", "प्युठान"),
            new District(provinceMap.get("Lumbini"), "Rolpa", "रोल्पा"),
            new District(provinceMap.get("Lumbini"), "Dang", "दाङ"),
            new District(provinceMap.get("Lumbini"), "Banke", "बाँके"),
            new District(provinceMap.get("Lumbini"), "Bardiya", "बर्दिया"),
            new District(provinceMap.get("Lumbini"), "Eastern Rukum", "पूर्वी रुकुम"),
            // Province 6 - Karnali
            new District(provinceMap.get("Karnali"), "Salyan", "सल्यान"),
            new District(provinceMap.get("Karnali"), "Surkhet", "सुर्खेत"),
            new District(provinceMap.get("Karnali"), "Dailekh", "दैलेख"),
            new District(provinceMap.get("Karnali"), "Jajarkot", "जाजरकोट"),
            new District(provinceMap.get("Karnali"), "Dolpa", "डोल्पा"),
            new District(provinceMap.get("Karnali"), "Jumla", "जुम्ला"),
            new District(provinceMap.get("Karnali"), "Kalikot", "कालिकोट"),
            new District(provinceMap.get("Karnali"), "Mugu", "मुगु"),
            new District(provinceMap.get("Karnali"), "Humla", "हुम्ला"),
            new District(provinceMap.get("Karnali"), "Western Rukum", "पश्चिमी रुकुम"),
            // Province 7 - Sudurpashchim
            new District(provinceMap.get("Sudurpashchim"), "Kailali", "कैलाली"),
            new District(provinceMap.get("Sudurpashchim"), "Achham", "अछाम"),
            new District(provinceMap.get("Sudurpashchim"), "Doti", "डोटी"),
            new District(provinceMap.get("Sudurpashchim"), "Bajhang", "बझाङ"),
            new District(provinceMap.get("Sudurpashchim"), "Bajura", "बाजुरा"),
            new District(provinceMap.get("Sudurpashchim"), "Kanchanpur", "कञ्चनपुर"),
            new District(provinceMap.get("Sudurpashchim"), "Dadeldhura", "डडेल्धुरा"),
            new District(provinceMap.get("Sudurpashchim"), "Baitadi", "बैतडी"),
            new District(provinceMap.get("Sudurpashchim"), "Darchula", "दार्चुला")
        );
        districtRepository.saveAll(districts);
        log.info("Seeded {} districts.", districts.size());
    }

    @Transactional
    public void seedMunicipalityVdcIfNeeded() {
        if (municipalityVdcRepository.count() > 0) {
            log.info("Municipalities already exist, skipping.");
            return;
        }
        log.info("Seeding municipalities...");
        Map<String, District> districtMap = new HashMap<>();
        for (District d : districtRepository.findAll()) {
            districtMap.put(d.getName(), d);
        }

        List<MunicipalityVdc> municipalities = new ArrayList<>();

        // ==============================
        // PROVINCE 1: KOSHI
        // ==============================

        // Taplejung
        municipalities.add(new MunicipalityVdc(districtMap.get("Taplejung"), "Phungling", "फुङ्लिङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Taplejung"), "Sidingwa", "सिदिङ्गवा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Taplejung"), "Phaktanglung", "फक्ताङलुङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Taplejung"), "Mikkwakhola", "मिक्वाखोला"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Taplejung"), "Meringden", "मेरिङदेन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Taplejung"), "Maiwakhola", "मैवाखोला"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Taplejung"), "Aathrai Triveni", "आठराई त्रिवेणी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Taplejung"), "Pathivara Yangwarak", "पाथीभरा याङवरक"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Taplejung"), "Sirijanga", "सिरिजङ्गा"));

        // Sankhuwasabha
        municipalities.add(new MunicipalityVdc(districtMap.get("Sankhuwasabha"), "Khandbari", "खाँदबारी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sankhuwasabha"), "Chainpur", "चैनपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sankhuwasabha"), "Madi", "मादी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sankhuwasabha"), "Dharmadevi", "धर्मादेवी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sankhuwasabha"), "Panchkhapan", "पाँचखपन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sankhuwasabha"), "Bhotkhola", "भोटखोला"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sankhuwasabha"), "Chichila", "चिचिला"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sankhuwasabha"), "Makalu", "मकालु"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sankhuwasabha"), "Silichong", "सिलिचोङ्ग"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sankhuwasabha"), "Savapokhari", "सावापोखरी"));

        // Solukhumbu
        municipalities.add(new MunicipalityVdc(districtMap.get("Solukhumbu"), "Sotang", "सोताङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Solukhumbu"), "Dudhkaushika", "दुधकौशिका"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Solukhumbu"), "Solu Dudhkunda", "सोलु दुधकुण्ड"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Solukhumbu"), "Nechasalyan", "नेचासल्यान"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Solukhumbu"), "Thulung Dudhkoshi", "थुलुङ दुधकोशी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Solukhumbu"), "Mahakulung", "महाकुलुङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Solukhumbu"), "Likhu Pike", "लिखु पिके"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Solukhumbu"), "Khumbu Pasang Lhamu", "खुम्बु पासाङ ल्हामु"));

        // Okhaldhunga
        municipalities.add(new MunicipalityVdc(districtMap.get("Okhaldhunga"), "Siddhicharan", "सिद्धिचरण"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Okhaldhunga"), "Khijidemba", "खिजिदेम्बा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Okhaldhunga"), "Champadevi", "चम्पादेवी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Okhaldhunga"), "Chishankhushma", "चिशङ्खुश्म"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Okhaldhunga"), "Manebhanjyang", "मानेभञ्ज्याङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Okhaldhunga"), "Molung", "मोलुङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Okhaldhunga"), "Likhu", "लिखु"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Okhaldhunga"), "Sunkoshi", "सुनकोशी"));

        // Khotang
        municipalities.add(new MunicipalityVdc(districtMap.get("Khotang"), "Halesi Tuwachung", "हलेसी तुवाचुङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Khotang"), "Rupakot Majhuwagadhi", "रुपाकोट मझुवागढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Khotang"), "Aiselukharka", "ऐसेलुखर्क"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Khotang"), "Lamidanda", "लामीडाँडा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Khotang"), "Jantedhunga", "जन्तेढुङ्गा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Khotang"), "Khotehang", "खोटेहाङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Khotang"), "Diprung", "दिप्रुङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Khotang"), "Kepilasgadhi", "केपिलासगढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Khotang"), "Baraha Pokhari", "बराहपोखरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Khotang"), "Sakela", "सकेला"));

        // Bhojpur
        municipalities.add(new MunicipalityVdc(districtMap.get("Bhojpur"), "Bhojpur", "भोजपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bhojpur"), "Shadananda", "षडानन्द"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bhojpur"), "Tyamkemaiyum", "ट्याम्केमैयुङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bhojpur"), "Ramprasad Rai", "रामप्रसाद राई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bhojpur"), "Pauwadungma", "पौवादुङ्मा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bhojpur"), "Hatuwagadhi", "हतुवागढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bhojpur"), "Aamchok", "आमचोक"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bhojpur"), "Arun", "अरुण"));

        // Dhankuta
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhankuta"), "Dhankuta", "धनकुटा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhankuta"), "Mahalaxmi", "महालक्ष्मी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhankuta"), "Khalsa Chhinsaphu", "खाल्सा छिन्साफु"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhankuta"), "Chaubise", "चौबिसे"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhankuta"), "Sahidbhumi", "सहिदभूमि"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhankuta"), "Chhathar Jorpati", "छथर जोरपाती"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhankuta"), "Sangurigadhi", "सङ्गुरीगढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhankuta"), "Tiling", "तिलिङ"));

        // Terhathum
        municipalities.add(new MunicipalityVdc(districtMap.get("Terhathum"), "Terhathum", "तेह्रथुम"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Terhathum"), "Aathrai", "आठराई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Terhathum"), "Chhathar", "छथर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Terhathum"), "Phedap", "फेदाप"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Terhathum"), "Menchayam", "मेन्छायाम"));

        // Panchthar
        municipalities.add(new MunicipalityVdc(districtMap.get("Panchthar"), "Phidim", "फिदिम"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Panchthar"), "Falgunanda", "फाल्गुनन्द"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Panchthar"), "Hilihang", "हिलिहाङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Panchthar"), "Kummayak", "कुम्मायक"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Panchthar"), "Miklajung", "मिक्लाजुङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Panchthar"), "Tumbewa", "तुम्बेवा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Panchthar"), "Yangwarak", "याङवरक"));

        // Ilam
        municipalities.add(new MunicipalityVdc(districtMap.get("Ilam"), "Ilam", "इलाम"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Ilam"), "Suryodaya", "सूर्योदय"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Ilam"), "Mai", "माइ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Ilam"), "Mangsebung", "माङ्सेबुङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Ilam"), "Deumai", "देउमाई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Ilam"), "Maijogmai", "माईजोगमाई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Ilam"), "Fakphokthum", "फाकफोकथुम"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Ilam"), "Chulachuli", "चुलाचुली"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Ilam"), "Sandakpur", "सन्दकपुर"));

        // Jhapa
        municipalities.add(new MunicipalityVdc(districtMap.get("Jhapa"), "Bhadrapur", "भद्रपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jhapa"), "Birtamod", "बिर्तामोड"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jhapa"), "Mechinagar", "मेचीनगर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jhapa"), "Damak", "दमक"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jhapa"), "Kankai", "कन्काई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jhapa"), "Gauradaha", "गौरादह"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jhapa"), "Barhadashi", "बर्हदशी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jhapa"), "Jhapa", "झापा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jhapa"), "Buddhashanti", "बुद्धशान्ति"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jhapa"), "Haldibari", "हल्दीबारी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jhapa"), "Kamal", "कमल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jhapa"), "Gauriganj", "गौरीगञ्ज"));

        // Morang
        municipalities.add(new MunicipalityVdc(districtMap.get("Morang"), "Biratnagar", "विराटनगर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Morang"), "Sundarharaicha", "सुन्दरहरैँचा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Morang"), "Belbari", "बेलबारी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Morang"), "Pathari Sanischare", "पथरी सनिश्चरे"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Morang"), "Urlabari", "उर्लाबारी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Morang"), "Rangeli", "रङ्गेली"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Morang"), "Koshi Haraicha", "कोशी हरैँचा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Morang"), "Letang Bhogateni", "लेटाङ भोगटेनी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Morang"), "Dhanpalthan", "धनपालथान"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Morang"), "Jahada", "जहदा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Morang"), "Budhiganga", "बुढीगङ्गा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Morang"), "Miklajung", "मिक्लाजुङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Morang"), "Katahari", "कटहरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Morang"), "Gramthan", "ग्रामथान"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Morang"), "Kerabari", "केराबारी"));

        // Sunsari
        municipalities.add(new MunicipalityVdc(districtMap.get("Sunsari"), "Itahari", "इटहरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sunsari"), "Dharan", "धरान"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sunsari"), "Inaruwa", "इनरुवा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sunsari"), "Duhabi", "दुहवी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sunsari"), "Ramdhuni", "रामधुनी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sunsari"), "Barahachhetra", "बराहक्षेत्र"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sunsari"), "Koshi", "कोशी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sunsari"), "Gadhi", "गढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sunsari"), "Bhokraha", "भोक्राहा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sunsari"), "Harinagara", "हरिनगर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sunsari"), "Dewanganj", "देवानगञ्ज"));

        // Udayapur
        municipalities.add(new MunicipalityVdc(districtMap.get("Udayapur"), "Triyuga", "त्रियुगा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Udayapur"), "Katari", "कटारी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Udayapur"), "Chaudandigadhi", "चौदण्डीगढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Udayapur"), "Belaka", "बेलका"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Udayapur"), "Udayapur Gadhi", "उदयपुरगढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Udayapur"), "Rautamai", "रौतामाई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Udayapur"), "Tapli", "ताप्ली"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Udayapur"), "Limchungbung", "लिम्चुङबुङ"));

        // ==============================
        // PROVINCE 2: MADHESH
        // ==============================

        // Saptari
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Rajbiraj", "राजविराज"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Kanchan Rup", "कञ्चनरुप"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Bode Barsain", "बोदे बरसाइन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Dakneshwari", "डाक्नेश्वरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Hanumannagar Kankalini", "हनुमाननगर कङ्कालिनी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Bishnupur", "विष्णुपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Tilathi Koiladi", "तिलाठी कोइलाडी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Saptakoshi", "सप्तकोशी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Surunga", "सुरुङ्गा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Belhi Chapena", "बेल्ही चपेना"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Bhangaha", "भङ्गाहा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Rupani", "रुपनी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Mahadeva", "महादेवा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Agnisair Krishnasavaran", "अग्नीसाइर कृष्णासवरन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Shambhunath", "शम्भुनाथ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Rajgadh", "राजगढ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Khadak", "खडक"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Saptari"), "Chhinnamasta", "छिन्नमस्ता"));

        // Siraha
        municipalities.add(new MunicipalityVdc(districtMap.get("Siraha"), "Lahan", "लहान"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Siraha"), "Dhangadhimai", "धनगढीमाई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Siraha"), "Siraha", "सिराहा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Siraha"), "Golbazar", "गोलबजार"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Siraha"), "Mirchaiya", "मिर्चैया"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Siraha"), "Kalyanpur", "कल्याणपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Siraha"), "Bhagawanpur", "भगवानपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Siraha"), "Karjanha", "कर्जन्हा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Siraha"), "Bariyarpatti", "बरियारपट्टी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Siraha"), "Sukhipur", "सुखीपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Siraha"), "Naraha", "नरहा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Siraha"), "Lakshmipur Patari", "लक्ष्मीपुर पतारी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Siraha"), "Arnama", "अर्नमा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Siraha"), "Aurahi", "औरही"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Siraha"), "Sakhuwanankarkatti", "सखुवानन्कारकट्टी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Siraha"), "Bishnupur", "विष्णुपुर"));

        // Dhanusha
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhanusha"), "Janakpurdham", "जनकपुरधाम"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhanusha"), "Dhanusadham", "धनुषाधाम"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhanusha"), "Mithila", "मिथिला"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhanusha"), "Sabaila", "सबैला"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhanusha"), "Hansapur", "हंसपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhanusha"), "Kamala", "कमला"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhanusha"), "Shahidnagar", "सहिदनगर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhanusha"), "Chhireshwarnath", "छिरेश्वरनाथ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhanusha"), "Bideha", "बिदेह"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhanusha"), "Lakshminiya", "लक्ष्मीनिया"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhanusha"), "Mithila Bihari", "मिथिला विहार"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhanusha"), "Janaka Nandini", "जनक नन्दिनी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhanusha"), "Aurahi", "औरही"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhanusha"), "Bateshwar", "बटेश्वर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhanusha"), "Dhanauji", "धनौजी"));

        // Mahottari
        municipalities.add(new MunicipalityVdc(districtMap.get("Mahottari"), "Jaleshwar", "जलेश्वर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mahottari"), "Bardibas", "बर्दिबास"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mahottari"), "Gaushala", "गौशाला"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mahottari"), "Balwa", "बलवा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mahottari"), "Matihani", "मटिहानी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mahottari"), "Aurahi", "औरही"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mahottari"), "Ramgopalpur", "रामगोपालपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mahottari"), "Manra Siswa", "मनरा शिसवा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mahottari"), "Samsi", "सम्सी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mahottari"), "Bhangaha", "भङ्गाहा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mahottari"), "Eakdara", "एकडारा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mahottari"), "Mahottari", "महोत्तरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mahottari"), "Loharpatti", "लोहारपट्टी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mahottari"), "Pipra", "पिपरा"));

        // Sarlahi
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Malangwa", "मलङ्गवा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Harivan", "हरिवन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Lalbandi", "लालबन्दी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Ishworpur", "ईश्वरपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Brahmapuri", "ब्रह्मपुरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Balara", "बलरा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Godaita", "गोडैटा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Chandranagar", "चन्द्रनगर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Karmaiya", "कर्मैया"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Basbariya", "बसबरिया"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Bishnu", "विष्णु"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Sundarpur", "सुन्दरपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Chakraghatta", "चक्रघट्टा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Barahathwa", "बरहथवा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Bagmati", "बागमती"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Haripurwa", "हरिपुरवा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Dhankaul", "धनकौल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Ramnagar", "रामनगर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Kaudena", "कौडेना"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sarlahi"), "Parsa", "पर्सा"));

        // Rautahat
        municipalities.add(new MunicipalityVdc(districtMap.get("Rautahat"), "Gaur", "गौर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rautahat"), "Garuda", "गरुडा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rautahat"), "Brindaban", "वृन्दावन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rautahat"), "Chandrapur", "चन्द्रपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rautahat"), "Dewahi Gonahi", "देवाही गोनाही"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rautahat"), "Gujara", "गुजरा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rautahat"), "Gadhimai", "गढीमाई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rautahat"), "Paroha", "परोहा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rautahat"), "Madhav Narayan", "माधव नारायण"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rautahat"), "Mithila", "मिथिला"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rautahat"), "Durga Bhagwati", "दुर्गा भगवती"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rautahat"), "Baudhimai", "बौधीमाई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rautahat"), "Rajdevi", "राजदेवी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rautahat"), "Yamunamai", "यमुनामाई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rautahat"), "Phatuwa Bijayapur", "फतुवा विजयपुर"));

        // Bara
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Kalaiya", "कलैया"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Jitpur Simara", "जीतपुर सिमरा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Mahagadhimai", "महागढीमाई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Simraungadh", "सिम्रौनगढ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Pacharauta", "पचरौता"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Gadhi", "गढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Bishnupur", "विष्णुपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Prasauni", "प्रसौनी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Nijgadh", "निजगढ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Baragadhi", "बारागढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Fattepur", "फत्तेपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Pheta", "फेटा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Karaiyamai", "करैयामाई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Devtal", "देवताल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Parwanipur", "पर्वानीपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Adarsh Kotwal", "आदर्श कोटवाल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Suwarna", "सुवर्ण"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bara"), "Terah Rauta", "तेरह रौता"));

        // Parsa
        municipalities.add(new MunicipalityVdc(districtMap.get("Parsa"), "Birgunj", "वीरगञ्ज"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parsa"), "Pokhariya", "पोखरिया"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parsa"), "Bahudarmai", "बहुदरमाई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parsa"), "Parsagadhi", "पर्सागढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parsa"), "Paterwa Sugauli", "पटेर्वा सुगौली"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parsa"), "Chhipaharmai", "छिपहरमाई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parsa"), "Jagar Nath", "जगरनाथपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parsa"), "Jirabhawani", "जिराभवानी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parsa"), "Kalikamai", "कालिकामाई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parsa"), "Pakaha Mainpur", "पकहा मैनपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parsa"), "Bindabasini", "बिन्दबासिनी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parsa"), "Sakhuwa Prasauni", "सखुवा प्रसौनी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parsa"), "Thori", "थोरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parsa"), "Dhobini", "धोबिनी"));

        // ==============================
        // PROVINCE 3: BAGMATI
        // ==============================

        // Sindhuli
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhuli"), "Kamalamai", "कमलामाई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhuli"), "Dudhauli", "दुधौली"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhuli"), "Sunkoshi", "सुनकोशी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhuli"), "Hariharpur Gadhi", "हरिहरपुरगढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhuli"), "Marin", "मरिण"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhuli"), "Golanjor", "गोलन्जोर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhuli"), "Phikkal", "फिक्कल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhuli"), "Tinpatan", "तिनपाटन"));

        // Ramechhap
        municipalities.add(new MunicipalityVdc(districtMap.get("Ramechhap"), "Manthali", "मन्थली"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Ramechhap"), "Ramechhap", "रामेछाप"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Ramechhap"), "Umakunda", "उमाकुण्ड"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Ramechhap"), "Khandadevi", "खाँडादेवी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Ramechhap"), "Gokulganga", "गोकुलगङ्गा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Ramechhap"), "Likhu Tamakoshi", "लिखु तामाकोशी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Ramechhap"), "Doramba", "दोरम्बा"));

        // Dolakha
        municipalities.add(new MunicipalityVdc(districtMap.get("Dolakha"), "Bhimeshwar", "भीमेश्वर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dolakha"), "Jiri", "जिरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dolakha"), "Kalinchok", "कालिञ्चोक"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dolakha"), "Melung", "मेलुङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dolakha"), "Baiteshwar", "बैतेश्वर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dolakha"), "Shailung", "शैलुङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dolakha"), "Thangwa", "थाङ्वा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dolakha"), "Dudhkoshi", "दुधकोशी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dolakha"), "Gaurishankar", "गौरीशङ्कर"));

        // Bhaktapur
        municipalities.add(new MunicipalityVdc(districtMap.get("Bhaktapur"), "Bhaktapur", "भक्तपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bhaktapur"), "Changu Narayan", "चाँगुनारायण"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bhaktapur"), "Suryabinayak", "सूर्यविनायक"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bhaktapur"), "Madhyapur Thimi", "मध्यपुर थिमि"));

        // Dhading
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhading"), "Nilkantha", "नीलकण्ठ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhading"), "Thakre", "थाक्रे"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhading"), "Benighat Rorang", "बेनीघाट रोराङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhading"), "Khaniyabash", "खनियाबास"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhading"), "Gangajamuna", "गङ्गाजमुना"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhading"), "Netrawati Dabjong", "नेत्रावती डबजोङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhading"), "Gajuri", "गजुरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhading"), "Galchhi", "गल्छी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhading"), "Siddhalek", "सिद्धलेक"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhading"), "Tripurasundari", "त्रिपुरासुन्दरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhading"), "Jwalamukhi", "ज्वालामुखी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dhading"), "Semjong", "सेमजोङ"));

        // Kathmandu
        municipalities.add(new MunicipalityVdc(districtMap.get("Kathmandu"), "Kathmandu", "काठमाडौँ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kathmandu"), "Kirtipur", "कीर्तिपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kathmandu"), "Chandragiri", "चन्द्रागिरि"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kathmandu"), "Budhanilthan", "बूढानीलकण्ठ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kathmandu"), "Tarakeshwar", "तारकेश्वर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kathmandu"), "Nagarjun", "नागार्जुन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kathmandu"), "Tokha", "टोखा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kathmandu"), "Gokarneshwar", "गोकर्णेश्वर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kathmandu"), "Shankharapur", "शङ्खरापुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kathmandu"), "Kageshwari Manohara", "कागेश्वरी मनोहरा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kathmandu"), "Dakshinkali", "दक्षिणकाली"));

        // Kavrepalanchok
        municipalities.add(new MunicipalityVdc(districtMap.get("Kavrepalanchok"), "Dhulikhel", "धुलिखेल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kavrepalanchok"), "Banepa", "बनेपा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kavrepalanchok"), "Panauti", "पनौती"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kavrepalanchok"), "Panchkhal", "पाँचखाल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kavrepalanchok"), "Mandan Deupur", "मण्डन देउपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kavrepalanchok"), "Namobuddha", "नमोबुद्ध"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kavrepalanchok"), "Temal", "तेमाल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kavrepalanchok"), "Bethanchowk", "बेथानचोक"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kavrepalanchok"), "Bhumlu", "भुम्लु"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kavrepalanchok"), "Chaurideurali", "चौंरीदेउराली"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kavrepalanchok"), "Mahabharat", "महाभारत"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kavrepalanchok"), "Roshi", "रोशी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kavrepalanchok"), "Khani Khola", "खानीखोला"));

        // Lalitpur
        municipalities.add(new MunicipalityVdc(districtMap.get("Lalitpur"), "Lalitpur", "ललितपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Lalitpur"), "Godawari", "गोदावरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Lalitpur"), "Mahalaxmi", "महालक्ष्मी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Lalitpur"), "Bagmati", "बागमती"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Lalitpur"), "Mahankal", "महाङ्काल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Lalitpur"), "Konjyoson", "कोन्ज्योसोम"));

        // Nuwakot
        municipalities.add(new MunicipalityVdc(districtMap.get("Nuwakot"), "Bidur", "विदुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nuwakot"), "Belkotgadhi", "बेलकोटगढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nuwakot"), "Tadi", "तादी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nuwakot"), "Likhu", "लिखु"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nuwakot"), "Shivapuri", "शिवपुरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nuwakot"), "Kispang", "किस्पाङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nuwakot"), "Myagang", "म्यागाङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nuwakot"), "Taruka", "तारुका"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nuwakot"), "Panchakanya", "पञ्चकन्या"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nuwakot"), "Kakani", "ककनी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nuwakot"), "Dupcheshwar", "दुप्चेश्वर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nuwakot"), "Suryagadhi", "सूर्यगढी"));

        // Rasuwa
        municipalities.add(new MunicipalityVdc(districtMap.get("Rasuwa"), "Dhunche", "धुन्चे"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rasuwa"), "Gosainkunda", "गोसाइँकुण्ड"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rasuwa"), "Aamachhodingmo", "आमाछोदिङ्मो"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rasuwa"), "Kalika", "कालिका"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rasuwa"), "Naukunda", "नौकुण्ड"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rasuwa"), "Pashupati", "पशुपति"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rasuwa"), "Uttargaya", "उत्तरगया"));

        // Sindhupalchok
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhupalchok"), "Chautara Sangachokgadhi", "चौतारा साँगाचोकगढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhupalchok"), "Balephi", "बलेफी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhupalchok"), "Sunkoshi", "सुनकोशी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhupalchok"), "Indrawati", "इन्द्रावती"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhupalchok"), "Lisankhu Pakhar", "लिशङ्खु पाखर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhupalchok"), "Jugal", "जुगल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhupalchok"), "Melamchi", "मेलम्ची"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhupalchok"), "Bhotekoshi", "भोटेकोशी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhupalchok"), "Tripurasundari", "त्रिपुरासुन्दरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhupalchok"), "Helambu", "हेलम्बु"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Sindhupalchok"), "Chankhu", "चाङ्खु"));

        // Chitwan
        municipalities.add(new MunicipalityVdc(districtMap.get("Chitwan"), "Bharatpur", "भरतपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Chitwan"), "Ratnanagar", "रत्ननगर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Chitwan"), "Khairahani", "खैरहनी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Chitwan"), "Kalika", "कालिका"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Chitwan"), "Madi", "मादी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Chitwan"), "Ichchhakamana", "इच्छाकामना"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Chitwan"), "Rapti", "राप्ती"));

        // Makwanpur
        municipalities.add(new MunicipalityVdc(districtMap.get("Makwanpur"), "Hetauda", "हेटौडा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Makwanpur"), "Thaha", "थाहा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Makwanpur"), "Bhimphedi", "भीमफेदी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Makwanpur"), "Makwanpurgadhi", "मकवानपुरगढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Makwanpur"), "Manahari", "मनहरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Makwanpur"), "Raksirang", "राक्सिराङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Makwanpur"), "Bakaiya", "बकैया"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Makwanpur"), "Bagmati", "बागमती"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Makwanpur"), "Kailash", "कैलाश"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Makwanpur"), "Indrasarowar", "इन्द्रसरोवर"));

        // ==============================
        // PROVINCE 4: GANDAKI
        // ==============================

        // Gorkha
        municipalities.add(new MunicipalityVdc(districtMap.get("Gorkha"), "Gorkha", "गोर्खा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gorkha"), "Palungtar", "पालुङटार"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gorkha"), "Sahid Lakhan", "सहिद लखन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gorkha"), "Chumnubri", "छुम्नुब्री"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gorkha"), "Ajirkot", "अजिरकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gorkha"), "Dharche", "धार्चे"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gorkha"), "Bhimsen", "भीमसेन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gorkha"), "Aarughat", "आरूघाट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gorkha"), "Gandaki", "गण्डकी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gorkha"), "Siranchok", "सिरानचोक"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gorkha"), "Barpak Sulikot", "वार्पाक सुलिकोट"));

        // Kaski
        municipalities.add(new MunicipalityVdc(districtMap.get("Kaski"), "Pokhara", "पोखरा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kaski"), "Annapurna", "अन्नपूर्ण"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kaski"), "Madi", "मादी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kaski"), "Rupa", "रूपा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kaski"), "Machhapuchchhre", "माछापुच्छ्रे"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kaski"), "Rupakot", "रुपाकोट"));

        // Lamjung
        municipalities.add(new MunicipalityVdc(districtMap.get("Lamjung"), "Besisahar", "बेसीशहर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Lamjung"), "Madhyanepal", "मध्यनेपाल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Lamjung"), "Rainas", "राईनास"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Lamjung"), "Sundarbazar", "सुन्दरबजार"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Lamjung"), "Dordi", "दोर्दी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Lamjung"), "Marsyangdi", "मर्स्याङ्दी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Lamjung"), "Kwholasothar", "क्व्होलासोथार"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Lamjung"), "Dudhpokhari", "दुधपोखरी"));

        // Syangja
        municipalities.add(new MunicipalityVdc(districtMap.get("Syangja"), "Putalibazar", "पुतलीबजार"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Syangja"), "Waling", "वालिङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Syangja"), "Siryang", "सिर्याङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Syangja"), "Birkot", "बिरकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Syangja"), "Chapakot", "चापाकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Syangja"), "Arjunchaupari", "अर्जुनचौपारी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Syangja"), "Phedikhola", "फेदीखोला"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Syangja"), "Harinas", "हरिनास"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Syangja"), "Kaligandaki", "कालीगण्डकी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Syangja"), "Aandhikhola", "आँधीखोला"));

        // Tanahun
        municipalities.add(new MunicipalityVdc(districtMap.get("Tanahun"), "Damauli", "दमौली"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Tanahun"), "Bhanu", "भानु"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Tanahun"), "Byas", "व्यास"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Tanahun"), "Bandipur", "बन्दीपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Tanahun"), "Shuklagandaki", "शुक्लागण्डकी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Tanahun"), "Anbukhaireni", "आँबुखैरेनी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Tanahun"), "Aanbu", "आँबु"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Tanahun"), "Ghiring", "घिरिङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Tanahun"), "Myagde", "म्याग्दे"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Tanahun"), "Devghat", "देवघाट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Tanahun"), "Rhishing", "ऋषिङ"));

        // Manang
        municipalities.add(new MunicipalityVdc(districtMap.get("Manang"), "Chame", "चामे"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Manang"), "Neshang", "नेशाङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Manang"), "Narpa Bhumi", "नार्पा भूमि"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Manang"), "Nasong", "नासों"));

        // Mustang
        municipalities.add(new MunicipalityVdc(districtMap.get("Mustang"), "Jomsom", "जोमसोम"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mustang"), "Thasang", "थासाङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mustang"), "Gharapjhong", "घरपझोङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mustang"), "Barhagaun Muktichhetra", "बार्हागाउँ मुक्तिक्षेत्र"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mustang"), "Lomanthang", "लोमन्थाङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mustang"), "Dalome", "डालोमे"));

        // Myagdi
        municipalities.add(new MunicipalityVdc(districtMap.get("Myagdi"), "Beni", "बेनी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Myagdi"), "Annapurna", "अन्नपूर्ण"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Myagdi"), "Raghuganga", "रघुगङ्गा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Myagdi"), "Dhaulagiri", "धौलागिरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Myagdi"), "Malika", "मालिका"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Myagdi"), "Mangala", "मङ्गला"));

        // Nawalpur
        municipalities.add(new MunicipalityVdc(districtMap.get("Nawalpur"), "Kawasoti", "कावासोती"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nawalpur"), "Gaindakot", "गैँडाकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nawalpur"), "Devachuli", "देवचुली"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nawalpur"), "Madhyabindu", "मध्यविन्दु"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nawalpur"), "Bungdikali", "बुङ्दीकाली"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nawalpur"), "Bulingtar", "बुलिङटार"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nawalpur"), "Hupsekot", "हुप्सेकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Nawalpur"), "Bauddikali", "बौदीकाली"));

        // Parbat
        municipalities.add(new MunicipalityVdc(districtMap.get("Parbat"), "Kusma", "कुश्मा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parbat"), "Phalede", "फलेवास"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parbat"), "Jaljala", "जलजला"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parbat"), "Modi", "मोदी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parbat"), "Bihadi", "बिहादी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parbat"), "Mahashila", "महाशिला"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parbat"), "Painyu", "पैंयु"));

        // Baglung
        municipalities.add(new MunicipalityVdc(districtMap.get("Baglung"), "Baglung", "बागलुङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Baglung"), "Galkot", "गलकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Baglung"), "Dhorpatan", "धौरपाटन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Baglung"), "Jaimini", "जैमिनी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Baglung"), "Bareng", "बरेङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Baglung"), "Badigad", "बडिगाड"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Baglung"), "Nisi", "निसी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Baglung"), "Taman Khola", "तमानखोला"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Baglung"), "Tara Khola", "ताराखोला"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Baglung"), "Kanti", "काँति"));

        // ==============================
        // PROVINCE 5: LUMBINI
        // ==============================

        // Kapilvastu
        municipalities.add(new MunicipalityVdc(districtMap.get("Kapilvastu"), "Kapilvastu", "कपिलवस्तु"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kapilvastu"), "Banganga", "बाणगङ्गा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kapilvastu"), "Buddhabhumi", "बुद्धभूमि"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kapilvastu"), "Shivaraj", "शिवराज"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kapilvastu"), "Krishnanagar", "कृष्णनगर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kapilvastu"), "Maharajganj", "महाराजगञ्ज"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kapilvastu"), "Mayadevi", "मायादेवी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kapilvastu"), "Suddhodhan", "शुद्धोधन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kapilvastu"), "Yashodhara", "यशोधरा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kapilvastu"), "Suryapura", "सूर्यपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kapilvastu"), "Bijayanagar", "विजयनगर"));

        // Parasi
        municipalities.add(new MunicipalityVdc(districtMap.get("Parasi"), "Ramgram", "रामग्राम"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parasi"), "Bardghat", "बर्दघाट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parasi"), "Sunawal", "सुनवल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parasi"), "Pratappur", "प्रतापपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parasi"), "Sarawal", "सरावल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parasi"), "Palhi Nandan", "पल्ही नन्दन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Parasi"), "Bauddar", "बौदार"));

        // Rupandehi
        municipalities.add(new MunicipalityVdc(districtMap.get("Rupandehi"), "Siddharthanagar", "सिद्धार्थनगर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rupandehi"), "Butwal", "बुटवल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rupandehi"), "Devdaha", "देवदह"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rupandehi"), "Lumbini Sanskritik", "लुम्बिनी सांस्कृतिक"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rupandehi"), "Sainamaina", "सैनामैना"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rupandehi"), "Sammarimai", "सम्मरीमाई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rupandehi"), "Rohini", "रोहिणी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rupandehi"), "Shuddhodhan", "शुद्धोधन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rupandehi"), "Mayadevi", "मायादेवी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rupandehi"), "Siyari", "सियारी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rupandehi"), "Gomai", "गोमाई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rupandehi"), "Tilottama", "तिलोत्तमा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rupandehi"), "Kanchan", "कञ्चन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rupandehi"), "Omsatiya", "ओमसतिया"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rupandehi"), "Marchawarimai", "मार्चावारीमाई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rupandehi"), "Kotahimai", "कोटहीमाई"));

        // Palpa
        municipalities.add(new MunicipalityVdc(districtMap.get("Palpa"), "Tansen", "तानसेन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Palpa"), "Rampur", "रामपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Palpa"), "Nisdi", "निस्दी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Palpa"), "Purbakhola", "पूर्वखोला"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Palpa"), "Ribdikot", "रिब्दिकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Palpa"), "Baganaskali", "बगनासकाली"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Palpa"), "Tinau", "तिनाउ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Palpa"), "Mathagadhi", "माथागढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Palpa"), "Rambha", "रम्भा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Palpa"), "Rainadevi Chhahara", "रैनादेवी छहरा"));

        // Gulmi
        municipalities.add(new MunicipalityVdc(districtMap.get("Gulmi"), "Musikot", "मुसिकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gulmi"), "Resunga", "रेसुङ्गा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gulmi"), "Gulmi", "गुल्मी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gulmi"), "Satyawati", "सत्यवती"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gulmi"), "Chandrakot", "चन्द्रकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gulmi"), "Ruru", "रुरु"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gulmi"), "Dhurkot", "धुर्कोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gulmi"), "Malika", "मालिका"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gulmi"), "Chhatrakot", "छत्रकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gulmi"), "Kaligandaki", "कालीगण्डकी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gulmi"), "Madane", "मदाने"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Gulmi"), "Ishma", "इश्मा"));

        // Arghakhanchi
        municipalities.add(new MunicipalityVdc(districtMap.get("Arghakhanchi"), "Sandhikharka", "सन्धिखर्क"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Arghakhanchi"), "Sitganga", "सितगङ्गा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Arghakhanchi"), "Bhumikasthan", "भूमिकास्थान"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Arghakhanchi"), "Chhatradev", "छत्रदेव"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Arghakhanchi"), "Panini", "पाणिनी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Arghakhanchi"), "Malarani", "मालारानी"));

        // Pyuthan
        municipalities.add(new MunicipalityVdc(districtMap.get("Pyuthan"), "Pyuthan", "प्युठान"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Pyuthan"), "Swargadwari", "स्वर्गद्वारी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Pyuthan"), "Naubahini", "नौबहिनी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Pyuthan"), "Sarumar", "सरुमार"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Pyuthan"), "Mallarani", "मल्लारानी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Pyuthan"), "Ayirabati", "अइरावती"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Pyuthan"), "Jhimruk", "झिम्रुक"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Pyuthan"), "Gaumukhi", "गौमुखी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Pyuthan"), "Mandavi", "मन्दवी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Pyuthan"), "Ligh", "लिघ"));

        // Rolpa
        municipalities.add(new MunicipalityVdc(districtMap.get("Rolpa"), "Rolpa", "रोल्पा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rolpa"), "Madi", "मादी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rolpa"), "Triveni", "त्रिवेणी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rolpa"), "Runtigadhi", "रुन्टीगढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rolpa"), "Lukam", "लुकाम"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rolpa"), "Sulichaur", "सुलिचौर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rolpa"), "Thawang", "थवाङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Rolpa"), "Duikholi", "दुइखोली"));

        // Dang
        municipalities.add(new MunicipalityVdc(districtMap.get("Dang"), "Ghorahi", "घोराही"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dang"), "Tulsipur", "तुलसीपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dang"), "Lamahi", "लमही"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dang"), "Dangisharan", "दङ्गीशरण"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dang"), "Shantinagar", "शान्तिनगर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dang"), "Gadawa", "गडवा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dang"), "Rajpur", "राजपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dang"), "Rapti", "राप्ती"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dang"), "Babai", "बबई"));

        // Banke
        municipalities.add(new MunicipalityVdc(districtMap.get("Banke"), "Nepalgunj", "नेपालगञ्ज"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Banke"), "Khajura", "खजुरा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Banke"), "Kohalpur", "कोहलपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Banke"), "Janaki", "जानकी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Banke"), "Rapti Sonari", "राप्ती सोनारी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Banke"), "Baijanath", "बैजनाथ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Banke"), "Naubasta", "नौबस्ता"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Banke"), "Duduwa", "डुडुवा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Banke"), "Narainapur", "नरैनापुर"));

        // Bardiya
        municipalities.add(new MunicipalityVdc(districtMap.get("Bardiya"), "Gulariya", "गुलरिया"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bardiya"), "Rajapur", "राजापुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bardiya"), "Madhuwan", "मधुवन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bardiya"), "Thakurbaba", "ठाकुरबाबा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bardiya"), "Bansgadhi", "बाँसगढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bardiya"), "Barbardiya", "बारबर्दिया"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bardiya"), "Badhaiyatal", "बढैयाताल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bardiya"), "Geruwa", "गेरुवा"));

        // Eastern Rukum
        municipalities.add(new MunicipalityVdc(districtMap.get("Eastern Rukum"), "Bhume", "भूमे"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Eastern Rukum"), "Putha Uttarganga", "पुथा उत्तरगङ्गा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Eastern Rukum"), "Sisne", "सिस्ने"));

        // ==============================
        // PROVINCE 6: KARNALI
        // ==============================

        // Salyan
        municipalities.add(new MunicipalityVdc(districtMap.get("Salyan"), "Salyan", "सल्यान"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Salyan"), "Sharada", "शारदा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Salyan"), "Dhorchaur", "धोरचौर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Salyan"), "Kalimati", "कालीमाटी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Salyan"), "Triveni", "त्रिवेणी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Salyan"), "Kapurkot", "कपुरकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Salyan"), "Chhatreshwari", "छत्रेश्वरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Salyan"), "Siddha Kumakh", "सिद्ध कुमाख"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Salyan"), "Bangad Kupinde", "बनगाड कुपिण्डे"));

        // Surkhet
        municipalities.add(new MunicipalityVdc(districtMap.get("Surkhet"), "Birendranagar", "वीरेन्द्रनगर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Surkhet"), "Bheriganga", "भेरीगङ्गा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Surkhet"), "Gurbhakot", "गुर्भाकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Surkhet"), "Panchpuri", "पञ्चपुरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Surkhet"), "Lekbeshi", "लेकबेसी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Surkhet"), "Simta", "सिम्ता"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Surkhet"), "Barahatal", "बराहताल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Surkhet"), "Chaukune", "चौकुने"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Surkhet"), "Chingad", "चिङ्गाड"));

        // Dailekh
        municipalities.add(new MunicipalityVdc(districtMap.get("Dailekh"), "Narayan", "नारायण"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dailekh"), "Dullu", "दुल्लु"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dailekh"), "Aathabis", "आठबीस"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dailekh"), "Bhairabi", "भैरवी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dailekh"), "Mahabu", "महाबु"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dailekh"), "Naumule", "नौमुले"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dailekh"), "Thantikhand", "थान्टीखण्ड"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dailekh"), "Bhagawatimai", "भगवतीमाई"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dailekh"), "Gamaudi", "गामौडी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dailekh"), "Dungeshwar", "दुर्गेश्वर"));

        // Jajarkot
        municipalities.add(new MunicipalityVdc(districtMap.get("Jajarkot"), "Bheri", "भेरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jajarkot"), "Chhedagad", "छेडागाड"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jajarkot"), "Nalgad", "नलगाड"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jajarkot"), "Shivalaya", "शिवालय"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jajarkot"), "Junichande", "जुनीचाँदे"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jajarkot"), "Kushe", "कुशे"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jajarkot"), "Barekot", "बारेकोट"));

        // Dolpa
        municipalities.add(new MunicipalityVdc(districtMap.get("Dolpa"), "Thuli Bheri", "ठूली भेरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dolpa"), "Tripurasundari", "त्रिपुरासुन्दरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dolpa"), "Dolpo Buddha", "डोल्पो बुद्ध"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dolpa"), "Shey Phoksundo", "शे फोक्सुण्डो"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dolpa"), "Chharka Tangsong", "छर्का ताङसोङ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dolpa"), "Mudkechula", "मुड्केचुला"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dolpa"), "Kaike", "काईके"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dolpa"), "Juphal", "जुफाल"));

        // Jumla
        municipalities.add(new MunicipalityVdc(districtMap.get("Jumla"), "Chandannath", "चन्दननाथ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jumla"), "Guthichaur", "गुठिचौर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jumla"), "Sinja", "सिंजा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jumla"), "Patarasi", "पातारासी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jumla"), "Kanaka Sundari", "कनक सुन्दरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jumla"), "Tila", "तिला"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jumla"), "Tatopani", "तातोपानी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Jumla"), "Hima", "हिँमा"));

        // Kalikot
        municipalities.add(new MunicipalityVdc(districtMap.get("Kalikot"), "Khandachakra", "खाँडाचक्र"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kalikot"), "Raskot", "रास्कोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kalikot"), "Tilagupha", "तिलागुफा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kalikot"), "Kalika", "कालिका"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kalikot"), "Pachaljharana", "पचालझरना"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kalikot"), "Sanni Triveni", "सान्नी त्रिवेणी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kalikot"), "Narharinath", "नरहरिनाथ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kalikot"), "Shubha Kalika", "शुभ कालिका"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kalikot"), "Mahawai", "महावै"));

        // Mugu
        municipalities.add(new MunicipalityVdc(districtMap.get("Mugu"), "Chhayanath Rara", "छायाँनाथ रारा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mugu"), "Khatyad", "खत्याड"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mugu"), "Soru", "सोरु"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Mugu"), "Mugum Karmarong", "मुगुम कार्मारोङ"));

        // Humla
        municipalities.add(new MunicipalityVdc(districtMap.get("Humla"), "Simkot", "सिमकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Humla"), "Namkha", "नाम्खा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Humla"), "Kharpunath", "खार्पुनाथ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Humla"), "Sarkegad", "सर्केगाड"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Humla"), "Chankheli", "चंखेली"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Humla"), "Tanjakot", "ताँजाकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Humla"), "Adanchuli", "अदानचुली"));

        // Western Rukum
        municipalities.add(new MunicipalityVdc(districtMap.get("Western Rukum"), "Aathabiskot", "आठबीसकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Western Rukum"), "Musikot", "मुसिकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Western Rukum"), "Chaurjahari", "चौरजहारी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Western Rukum"), "Sani Bheri", "सानी भेरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Western Rukum"), "Triveni", "त्रिवेणी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Western Rukum"), "Banphikot", "बाँफिकोट"));

        // ==============================
        // PROVINCE 7: SUDURPASHCHIM
        // ==============================

        // Kailali
        municipalities.add(new MunicipalityVdc(districtMap.get("Kailali"), "Dhangadhi", "धनगढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kailali"), "Tikapur", "टीकापुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kailali"), "Ghodaghodi", "घोडाघोडी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kailali"), "Lamkichuha", "लम्कीचुहा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kailali"), "Bhajani", "भजनी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kailali"), "Godawari", "गोदावरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kailali"), "Gauriganga", "गौरीगंगा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kailali"), "Janaki", "जानकी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kailali"), "Bardagoriya", "बर्दगोरिया"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kailali"), "Mohanyal", "मोहन्याल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kailali"), "Kailari", "कैलारी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kailali"), "Joshipur", "जोशीपुर"));

        // Achham
        municipalities.add(new MunicipalityVdc(districtMap.get("Achham"), "Mangalsen", "मङ्गलसेन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Achham"), "Panchadewal Binayak", "पञ्चदेवल विनायक"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Achham"), "Chaurpati", "चौरपाटी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Achham"), "Mellekh", "मेल्लेख"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Achham"), "Bannigadi Jayagad", "बान्नीगढी जयगढ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Achham"), "Sanfebagar", "साँफेबगर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Achham"), "Kamalbazar", "कमलबजार"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Achham"), "Ramarosan", "रामारोशन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Achham"), "Dhakari", "ढकारी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Achham"), "Turmakhand", "तुर्माखाँद"));

        // Doti
        municipalities.add(new MunicipalityVdc(districtMap.get("Doti"), "Dipayal Silgadhi", "दिपायल सिलगढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Doti"), "Aadarsha", "आदर्श"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Doti"), "K I Singh", "केआइसिंह"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Doti"), "Bogatan Phudsil", "बोगटान फुड्सिल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Doti"), "Badikedar", "बडिकेदार"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Doti"), "Jorayal", "जोरायल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Doti"), "Purbichauki", "पूर्विचौकी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Doti"), "Sayal", "सयल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Doti"), "Shikhar", "शिखर"));

        // Bajhang
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajhang"), "Jaya Prithvi", "जयपृथ्वी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajhang"), "Bungal", "बुङ्गल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajhang"), "Durgathali", "दुर्गाथली"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajhang"), "Thalara", "थलारा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajhang"), "Chhabis Pathibhara", "छबिस पाथीभरा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajhang"), "Khaptad Chhededaha", "खप्तड छेडेदह"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajhang"), "Kedarseu", "केदारस्यू"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajhang"), "Bitthadchir", "बित्थडचिर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajhang"), "Masta", "मष्टा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajhang"), "Talkot", "तालकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajhang"), "Surma", "सुर्मा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajhang"), "Saipal", "साइपाल"));

        // Bajura
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajura"), "Badimalika", "बडीमालिका"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajura"), "Triveni", "त्रिवेणी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajura"), "Budhinanda", "बुढीनन्दा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajura"), "Budhiganga", "बुढीगङ्गा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajura"), "Chhedagad", "छेडागाड"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajura"), "Gaumul", "गौमुल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajura"), "Himali", "हिमाली"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajura"), "Khaptad", "खप्तड"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Bajura"), "Swami Kartik", "स्वामीकार्तिक"));

        // Kanchanpur
        municipalities.add(new MunicipalityVdc(districtMap.get("Kanchanpur"), "Bhimdatta", "भीमदत्त"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kanchanpur"), "Bedkot", "बेदकोट"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kanchanpur"), "Belauri", "बेलौरी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kanchanpur"), "Krishnapur", "कृष्णपुर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kanchanpur"), "Laljhadi", "लालझाडी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kanchanpur"), "Mahakali", "महाकाली"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kanchanpur"), "Punarbas", "पुनर्बस"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kanchanpur"), "Shuklaphanta", "शुक्लाफाँटा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Kanchanpur"), "Dodhara Chandani", "दोधारा चाँदनी"));

        // Dadeldhura
        municipalities.add(new MunicipalityVdc(districtMap.get("Dadeldhura"), "Dasharath Chand", "दशरथ चन्द"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dadeldhura"), "Ajaymeru", "अजयमेरु"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dadeldhura"), "Amargadhi", "अमरगढी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dadeldhura"), "Parashuram", "परशुराम"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dadeldhura"), "Alital", "अलिताल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dadeldhura"), "Bhageshwar", "भागेश्वर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dadeldhura"), "Nawadurga", "नवदुर्ग"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Dadeldhura"), "Ganyapadhura", "गन्यापधुरा"));

        // Baitadi
        municipalities.add(new MunicipalityVdc(districtMap.get("Baitadi"), "Dasharathchand", "दशरथचन्द"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Baitadi"), "Patan", "पाटन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Baitadi"), "Melauli", "मेलौली"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Baitadi"), "Purchaundi", "पुर्चौडी"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Baitadi"), "Sunarya", "सुनार्या"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Baitadi"), "Shivanath", "शिवनाथ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Baitadi"), "Pancheshwar", "पञ्चेश्वर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Baitadi"), "Dogdakedar", "दोगडाकेदार"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Baitadi"), "Dilasaini", "दिलासैनी"));

        // Darchula
        municipalities.add(new MunicipalityVdc(districtMap.get("Darchula"), "Mahakali", "महाकाली"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Darchula"), "Malikarjun", "मालिकार्जुन"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Darchula"), "Shailyashikhar", "शैल्यशिखर"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Darchula"), "Apihimal", "अपिहिमाल"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Darchula"), "Duhun", "दुहुँ"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Darchula"), "Vyans", "व्यास"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Darchula"), "Marma", "मार्मा"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Darchula"), "Naugad", "नौगाड"));
        municipalities.add(new MunicipalityVdc(districtMap.get("Darchula"), "Lekam", "लेकम"));

        municipalityVdcRepository.saveAll(municipalities);
        log.info("Seeded {} municipalities.", municipalities.size());
    }
}
